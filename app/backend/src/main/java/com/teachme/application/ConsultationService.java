package com.teachme.application;

import com.teachme.ai.AiCallContext;
import com.teachme.ai.AiCallKind;
import com.teachme.ai.LanguageModel;
import com.teachme.ai.LanguageModel.MentorReply;
import com.teachme.ai.LanguageModel.MentorReplyInput;
import com.teachme.ai.Media;
import com.teachme.application.DialogueService.Recognition;
import com.teachme.application.DialogueService.Recording;
import com.teachme.application.DialogueService.ReplyTicket;
import com.teachme.domain.BoardWrite;
import com.teachme.domain.DialogueKind;
import com.teachme.domain.DialogueLimits;
import com.teachme.domain.MaterialStatus;
import com.teachme.domain.OrganizedProblem;
import com.teachme.domain.UserSettings;
import com.teachme.persistence.DialogueRepository;
import com.teachme.persistence.DialogueRepository.NewTurn;
import com.teachme.persistence.DialogueRepository.RecordedReply;
import com.teachme.persistence.DialogueRepository.Subtitle;
import com.teachme.persistence.MaterialRepository;
import com.teachme.persistence.MaterialRepository.MaterialRow;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 先輩先生への相談 (仕様書 5.4、アーキテクチャ設計 2.4、4.4)。開始、発話の認識、応答、音声、中断を受け持つ。
 *
 * <p>発話の受け付けと、応答の確定の検査と排他は {@link DialogueService} に任せる。ここでは相談に固有の部分を行う。整理済みの教材の選択、先輩先生の応答 (LLM を 1
 * 回だけ呼ぶ)、ボードへの書き込みの検査。学習評価は無いため、度合いは扱わない。
 *
 * <p>外部 AI の失敗は、外へ出すときに 502 の失敗 ({@link ExternalFailureException}) に変える。中身は、ログにも出さない。
 */
public final class ConsultationService {

  static final String MENTOR_REPLY_FAILED_CODE = "mentor_reply_failed";
  static final String MENTOR_REPLY_FAILED_MESSAGE = "先輩先生の応答を作れませんでした。もう一度話してください";

  private static final DialogueKind KIND = DialogueKind.CONSULTATION;
  private static final String BOARD_MIME_TYPE = "image/png";
  private static final Logger LOG = LoggerFactory.getLogger(ConsultationService.class);

  private final SettingsService settings;
  private final DialogueService dialogue;
  private final DialogueRepository dialogues;
  private final MaterialRepository materials;
  private final LanguageModel llm;
  private final Function<UUID, DialogueLimits> limits;
  private final Clock clock;

  /**
   * @param limits 利用者の相談の上限。時間は、テストの上書きを反映する
   */
  public ConsultationService(
      SettingsService settings,
      DialogueService dialogue,
      DialogueRepository dialogues,
      MaterialRepository materials,
      LanguageModel llm,
      Function<UUID, DialogueLimits> limits,
      Clock clock) {
    this.settings = settings;
    this.dialogue = dialogue;
    this.dialogues = dialogues;
    this.materials = materials;
    this.llm = llm;
    this.limits = limits;
    this.clock = clock;
  }

  /**
   * 相談の開始の結果。問題文と図表だけを持つ。模範解答、解説、指導のポイントは、先輩先生の材料であり、利用者には見せない。
   *
   * @param serverNow サーバーの現在時刻。クライアントの時計のずれを直すために使う
   * @param startedAt 開始時刻 (サーバーが決める)
   * @param deadlineAt 期限
   * @param maxRounds 往復の上限
   * @param figures 問題の図表。問題文の下に、この順で置く
   */
  public record Started(
      UUID consultationId,
      Instant serverNow,
      Instant startedAt,
      Instant deadlineAt,
      int maxRounds,
      String problemText,
      List<OrganizedProblem.Figure> figures) {}

  /**
   * 先輩先生の字幕 1 行。
   *
   * @param turnId 字幕の識別子。音声の取得に使う
   */
  public record Turn(UUID turnId, String text) {}

  /**
   * 先輩先生の応答。
   *
   * @param boardWrites ボードに書き込む内容。無ければ空
   * @param roundCount この応答を含めた往復の数
   * @param limitReached 往復が上限に達した、または期限を過ぎたか
   */
  public record Reply(
      Turn turn, List<BoardWrite> boardWrites, int roundCount, boolean limitReached) {}

  /**
   * 整理済みの教材を使って、相談を始める。同じ利用者の進行中の相談は、中断として消す。
   *
   * @throws ConflictException 設定が未完了、個人情報の確認が無い、教材が整理済みでない
   * @throws NotFoundException 教材が無い、または今の単元の教材でない
   */
  public Started start(UUID userId, UUID materialId) {
    dialogue.requireReady(userId);
    UserSettings current = settings.requireCompleted(userId);
    MaterialRow material =
        materials
            .find(userId, materialId)
            .filter(row -> row.unitId().equals(current.unitId()))
            .orElseThrow(NotFoundException::new);
    if (material.status() != MaterialStatus.ORGANIZED || material.problem() == null) {
      throw new ConflictException(
          LessonService.MATERIAL_NOT_ORGANIZED_CODE, LessonService.MATERIAL_NOT_ORGANIZED_MESSAGE);
    }
    OrganizedProblem problem = material.problem();

    Instant startedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
    Instant deadlineAt = limits.apply(userId).deadlineAt(startedAt);
    UUID consultationId = UUID.randomUUID();
    dialogues.startConsultation(userId, material.id(), consultationId, startedAt, deadlineAt);
    return new Started(
        consultationId,
        clock.instant(),
        startedAt,
        deadlineAt,
        KIND.limits().maxRounds(),
        problem.problemText(),
        problem.figures());
  }

  /**
   * 録音を文章にし、認識できたら利用者の字幕として入れる。応答は作らない。
   *
   * @return 認識できたときの字幕。認識できなかったとき空
   * @throws ConflictException 上限に達した (`limit_reached`)、処理中 (`busy`)、個人情報の確認が無い
   * @throws NotFoundException 相談が無い。STT の後に中断されたときも含む
   */
  public Optional<Recognition> utter(UUID userId, UUID consultationId, Recording recording) {
    return dialogue.acceptUtterance(KIND, userId, consultationId, recording);
  }

  /**
   * 認識できた発話に対する、先輩先生の応答を作る。LLM を 1 回だけ呼ぶ。
   *
   * <p>失敗したときは、往復を増やさず、利用者の字幕を残す。利用者は話し直せる。
   *
   * @param boardPng ボードの画像 (PNG)
   * @param pointedRegions 指している範囲。無ければ空
   * @throws ExternalFailureException 先輩先生の応答が失敗した (`mentor_reply_failed`)
   * @throws ConflictException 応答済み、置き換えられた、上限に達した、処理中など
   * @throws NotFoundException 相談が無い。中断された後も含む
   */
  public Reply reply(
      UUID userId,
      UUID consultationId,
      UUID utteranceId,
      byte[] boardPng,
      List<OrganizedProblem.Region> pointedRegions) {
    try (ReplyTicket ticket = dialogue.beginReply(KIND, userId, consultationId, utteranceId)) {
      OrganizedProblem problem = organizedProblemOf(userId, ticket.parent().materialId());
      MentorReply mentor =
          mentorReplyWithLlm(
              userId,
              new MentorReplyInput(
                  problem.problemText(),
                  problem.modelAnswer(),
                  problem.explanationSteps(),
                  problem.teachingPoints(),
                  new Media(BOARD_MIME_TYPE, boardPng),
                  ticket.utteranceText(),
                  subtitlesExcept(userId, consultationId, utteranceId),
                  pointedRegions.stream().map(ConsultationService::toLlmRegion).toList()));
      List<BoardWrite> boardWrites = toBoardWrites(mentor.boardWrites());
      RecordedReply recorded =
          dialogue.commitReply(
              ticket, NewTurn.mentor(mentor.text(), boardWrites), Optional.empty());

      DialogueLimits kindLimits = KIND.limits();
      boolean limitReached =
          kindLimits.isRoundLimitReached(recorded.roundCount())
              || kindLimits.isDeadlineReached(ticket.parent().deadlineAt(), clock.instant());
      return new Reply(
          new Turn(recorded.turnId(), mentor.text()),
          boardWrites,
          recorded.roundCount(),
          limitReached);
    }
  }

  /** 字幕の音声を返す。字幕 1 行につき、音声の生成は 1 回だけ。 */
  public byte[] audio(UUID userId, UUID consultationId, UUID turnId) {
    return dialogue.synthesizeAudio(KIND, userId, consultationId, turnId);
  }

  /** 相談を中断する。記録せず、字幕を消す。進行中の相談が無ければ何もしない。 */
  public void abandon(UUID userId, UUID consultationId) {
    dialogues.deleteParent(KIND, userId, consultationId);
  }

  /** 先輩先生の応答。LLM が失敗したら、往復を増やさないために失敗にする。1 回だけ呼び、やり直さない。 */
  private MentorReply mentorReplyWithLlm(UUID userId, MentorReplyInput input) {
    MentorReply reply;
    try {
      reply = llm.mentorReply(new AiCallContext(userId, AiCallKind.MENTOR_REPLY), input);
    } catch (RuntimeException e) {
      LOG.warn("先輩先生の応答に失敗しました: {}", e.getClass().getName());
      throw mentorReplyFailed();
    }
    if (reply == null || isBlank(reply.text())) {
      LOG.warn("先輩先生の応答が空でした");
      throw mentorReplyFailed();
    }
    return reply;
  }

  private static ExternalFailureException mentorReplyFailed() {
    return new ExternalFailureException(MENTOR_REPLY_FAILED_CODE, MENTOR_REPLY_FAILED_MESSAGE);
  }

  private OrganizedProblem organizedProblemOf(UUID userId, UUID materialId) {
    OrganizedProblem problem =
        materials
            .find(userId, materialId)
            .map(MaterialRow::problem)
            .orElseThrow(NotFoundException::new);
    if (problem == null) {
      throw new NotFoundException();
    }
    return problem;
  }

  /** 利用者の相談の字幕。応答の対象の発話 (`excluded`) は、発話の文章として別に渡すため除く。 */
  private List<LanguageModel.Subtitle> subtitlesExcept(
      UUID userId, UUID consultationId, UUID excluded) {
    return dialogues.listSubtitles(KIND, userId, consultationId).stream()
        .filter(subtitle -> !subtitle.id().equals(excluded))
        .map(ConsultationService::toLlmSubtitle)
        .toList();
  }

  /** ボードへの書き込みを検査する。形が外れたものは無いものとし、応答は止めない。文字の書き込みは空でない文字を、範囲の強調は画像の中の範囲を持つものだけを残す。 */
  private static List<BoardWrite> toBoardWrites(List<LanguageModel.BoardWrite> writes) {
    if (writes == null) {
      return List.of();
    }
    return writes.stream()
        .map(ConsultationService::toBoardWrite)
        .flatMap(Optional::stream)
        .toList();
  }

  private static Optional<BoardWrite> toBoardWrite(LanguageModel.BoardWrite write) {
    if (write == null || write.kind() == null) {
      return Optional.empty();
    }
    OrganizedProblem.Region region = write.region() == null ? null : fromLlmRegion(write.region());
    if (region != null && !region.fitsInImage()) {
      return Optional.empty();
    }
    return switch (write.kind()) {
      case TEXT ->
          isBlank(write.text())
              ? Optional.empty()
              : Optional.of(new BoardWrite(BoardWrite.Kind.TEXT, write.text(), region));
      case HIGHLIGHT ->
          region == null
              ? Optional.empty()
              : Optional.of(new BoardWrite(BoardWrite.Kind.HIGHLIGHT, null, region));
    };
  }

  private static LanguageModel.Subtitle toLlmSubtitle(Subtitle subtitle) {
    return new LanguageModel.Subtitle(
        LanguageModel.Speaker.valueOf(subtitle.speaker().name()), subtitle.body());
  }

  private static LanguageModel.Region toLlmRegion(OrganizedProblem.Region region) {
    return new LanguageModel.Region(region.x(), region.y(), region.width(), region.height());
  }

  private static OrganizedProblem.Region fromLlmRegion(LanguageModel.Region region) {
    return new OrganizedProblem.Region(region.x(), region.y(), region.width(), region.height());
  }

  private static boolean isBlank(String text) {
    return text == null || text.isBlank();
  }
}
