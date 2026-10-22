package com.teachme.application;

import com.teachme.ai.AiCallContext;
import com.teachme.ai.AiCallKind;
import com.teachme.ai.LanguageModel;
import com.teachme.ai.LanguageModel.Evaluation;
import com.teachme.ai.LanguageModel.EvaluationInput;
import com.teachme.ai.LanguageModel.StudentReply;
import com.teachme.ai.LanguageModel.StudentReplyInput;
import com.teachme.ai.LanguageModel.SummaryInput;
import com.teachme.ai.LanguageModel.SummaryResult;
import com.teachme.ai.Media;
import com.teachme.application.DialogueService.Processing;
import com.teachme.application.DialogueService.Recognition;
import com.teachme.application.DialogueService.Recording;
import com.teachme.application.DialogueService.ReplyTicket;
import com.teachme.domain.Annotation;
import com.teachme.domain.Degrees;
import com.teachme.domain.DialogueKind;
import com.teachme.domain.DialogueLimits;
import com.teachme.domain.Expression;
import com.teachme.domain.MaterialStatus;
import com.teachme.domain.OrganizedProblem;
import com.teachme.domain.StudentType;
import com.teachme.domain.TodayMode;
import com.teachme.domain.UserSettings;
import com.teachme.domain.WeekRange;
import com.teachme.persistence.DialogueRepository;
import com.teachme.persistence.DialogueRepository.NewTurn;
import com.teachme.persistence.DialogueRepository.ParentState;
import com.teachme.persistence.DialogueRepository.RecordedReply;
import com.teachme.persistence.DialogueRepository.Subtitle;
import com.teachme.persistence.LessonRecordRepository;
import com.teachme.persistence.LessonRecordRepository.RecordedLesson;
import com.teachme.persistence.MaterialRepository;
import com.teachme.persistence.MaterialRepository.MaterialRow;
import com.teachme.persistence.TodayModeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 授業 (仕様書 5.5、5.6、アーキテクチャ設計 2.2、4.4)。開始、発話の認識、応答、音声、まとめ、中断を受け持つ。
 *
 * <p>発話の受け付けと、応答の確定の検査と排他は {@link DialogueService}
 * に任せる。ここでは授業に固有の部分を行う。最初の発話、生徒の応答と学習評価の並行な呼び出し、まとめ、理解度の更新。
 *
 * <p>外部 AI の失敗は、外へ出すときに 502 の失敗 ({@link ExternalFailureException}) に変える。中身は、ログにも出さない。
 */
public final class LessonService {

  static final String MODE_NOT_NORMAL_CODE = "mode_not_normal";
  static final String MODE_NOT_NORMAL_MESSAGE = "今日は授業を始められない日です";
  static final String MATERIAL_NOT_ORGANIZED_CODE = "material_not_organized";
  static final String MATERIAL_NOT_ORGANIZED_MESSAGE = "整理済みの教材を選んでください";
  static final String STUDENT_REPLY_FAILED_CODE = "student_reply_failed";
  static final String STUDENT_REPLY_FAILED_MESSAGE = "生徒の応答を作れませんでした。もう一度話してください";
  static final String SUMMARY_FAILED_CODE = "summary_failed";
  static final String SUMMARY_FAILED_MESSAGE = "まとめノートを作れませんでした";
  static final String LIMIT_NOT_REACHED_CODE = "limit_not_reached";
  static final String LIMIT_NOT_REACHED_MESSAGE = "授業はまだ続けられます";

  /** 最初の発話の LLM が失敗したときに返す、固定の文言。仕様書に定めが無いため、授業が始められなくなることを避けて決めた (アーキテクチャ設計 9 節)。 */
  static final String FALLBACK_OPENING = "よろしくお願いします。この問題、ここまでは考えたのですが、続きを教えてください。";

  /** まとめの受け付けの猶予。クライアントの時計のずれを許す (アーキテクチャ設計 4.4)。 */
  static final Duration SUMMARY_GRACE = Duration.ofSeconds(3);

  private static final DialogueKind KIND = DialogueKind.LESSON;
  private static final String BOARD_MIME_TYPE = "image/png";
  private static final Logger LOG = LoggerFactory.getLogger(LessonService.class);

  /** 生徒の応答と学習評価を並べて呼ぶためのスレッド。どちらも外部の応答を待つだけなので、数は少なくてよい。 */
  private static final ExecutorService PARALLEL =
      Executors.newCachedThreadPool(LessonService::daemonThread);

  private final SettingsService settings;
  private final DialogueService dialogue;
  private final DialogueRepository dialogues;
  private final MaterialRepository materials;
  private final TodayModeRepository todayModes;
  private final LessonRecordRepository lessonRecords;
  private final LanguageModel llm;
  private final Function<UUID, DialogueLimits> limits;
  private final Clock clock;

  /**
   * @param limits 利用者の授業の上限。時間は、テストの上書きを反映する
   */
  public LessonService(
      SettingsService settings,
      DialogueService dialogue,
      DialogueRepository dialogues,
      MaterialRepository materials,
      TodayModeRepository todayModes,
      LessonRecordRepository lessonRecords,
      LanguageModel llm,
      Function<UUID, DialogueLimits> limits,
      Clock clock) {
    this.settings = settings;
    this.dialogue = dialogue;
    this.dialogues = dialogues;
    this.materials = materials;
    this.todayModes = todayModes;
    this.lessonRecords = lessonRecords;
    this.llm = llm;
    this.limits = limits;
    this.clock = clock;
  }

  /**
   * 授業の開始の結果。
   *
   * @param serverNow サーバーの現在時刻。クライアントの時計のずれを直すために使う
   * @param startedAt 開始時刻 (サーバーが決める)
   * @param deadlineAt 期限
   * @param maxRounds 往復の上限
   * @param board ボードの初期配置の材料。模範解答と解説は含めない
   * @param opening AI 生徒の最初の発話 (往復には数えない)
   */
  public record Started(
      UUID lessonId,
      Instant serverNow,
      Instant startedAt,
      Instant deadlineAt,
      int maxRounds,
      StudentType student,
      Board board,
      Turn opening) {}

  /**
   * ボードの初期配置の材料 (仕様書 5.5)。
   *
   * @param figures 問題の図表。問題文の下に、この順で置く
   */
  public record Board(
      String problemText, List<OrganizedProblem.Figure> figures, String studentWork) {}

  /**
   * AI 生徒の字幕 1 行。
   *
   * @param turnId 字幕の識別子。音声の取得に使う
   */
  public record Turn(UUID turnId, String text, Expression expression) {}

  /**
   * AI 生徒の応答。
   *
   * @param annotation ボードに示す印。無ければ空
   * @param roundCount この応答を含めた往復の数
   * @param limitReached 往復が上限に達した、または期限を過ぎたか
   */
  public record Reply(
      Turn turn, Optional<Annotation> annotation, int roundCount, boolean limitReached) {}

  /**
   * 記録したまとめノート。
   *
   * @param lessonsThisWeek 今週に記録した授業の回数 (全単元を合わせる)
   */
  public record Summary(
      UUID recordId,
      Instant completedAt,
      String summaryText,
      List<String> quotes,
      int lessonsThisWeek) {}

  /**
   * 授業を始める。最初の発話は、問題文と生徒の途中の解答だけで作る。LLM が失敗したときは、固定の文言で始める (進行を止めない)。
   *
   * <p>同じ利用者の進行中の授業は、中断として消す。
   *
   * @throws ConflictException 設定が未完了、個人情報の確認が無い、今日のモードが通常でない、教材が整理済みでない
   * @throws NotFoundException 教材が無い、または今の単元の教材でない
   */
  public Started start(UUID userId, UUID materialId) {
    dialogue.requireReady(userId);
    UserSettings current = settings.requireCompleted(userId);
    if (todayModes.effectiveMode(userId, LocalDate.now(clock)) != TodayMode.NORMAL) {
      throw new ConflictException(MODE_NOT_NORMAL_CODE, MODE_NOT_NORMAL_MESSAGE);
    }
    MaterialRow material =
        materials
            .find(userId, materialId)
            .filter(row -> row.unitId().equals(current.unitId()))
            .orElseThrow(NotFoundException::new);
    if (material.status() != MaterialStatus.ORGANIZED || material.problem() == null) {
      throw new ConflictException(MATERIAL_NOT_ORGANIZED_CODE, MATERIAL_NOT_ORGANIZED_MESSAGE);
    }
    OrganizedProblem problem = material.problem();
    Line opening = openingLine(userId, current.studentType(), problem);

    Instant startedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
    Instant deadlineAt = limits.apply(userId).deadlineAt(startedAt);
    UUID lessonId = UUID.randomUUID();
    UUID openingId = UUID.randomUUID();
    dialogues.startLesson(
        userId,
        current.unitId(),
        material.id(),
        lessonId,
        startedAt,
        deadlineAt,
        openingId,
        NewTurn.student(opening.text(), opening.expression(), Optional.empty()));
    return new Started(
        lessonId,
        clock.instant(),
        startedAt,
        deadlineAt,
        KIND.limits().maxRounds(),
        current.studentType(),
        new Board(problem.problemText(), problem.figures(), problem.studentWork()),
        new Turn(openingId, opening.text(), opening.expression()));
  }

  /**
   * 録音を文章にし、認識できたら利用者の字幕として入れる。応答は作らない。
   *
   * @return 認識できたときの字幕。認識できなかったとき空
   * @throws ConflictException 上限に達した (`limit_reached`)、処理中 (`busy`)、個人情報の確認が無い
   * @throws NotFoundException 授業が無い。STT の後に中断されたときも含む
   */
  public Optional<Recognition> utter(UUID userId, UUID lessonId, Recording recording) {
    return dialogue.acceptUtterance(KIND, userId, lessonId, recording);
  }

  /**
   * 認識できた発話に対する、生徒の応答と学習評価を作る。2 つの LLM を並べて呼び、両方が終わってから応答を確定する。
   *
   * <p>生徒の応答が失敗したときは、往復も度合いも変えず、失敗として返す。学習評価だけが失敗したときは、度合いを変えずに応答する。
   *
   * @param boardPng ボードの画像 (PNG)
   * @param pointedRegions 指している範囲。無ければ空
   * @throws ExternalFailureException 生徒の応答が失敗した (`student_reply_failed`)
   * @throws ConflictException 応答済み、置き換えられた、上限に達した、処理中など
   * @throws NotFoundException 授業が無い。中断された後も含む
   */
  public Reply reply(
      UUID userId,
      UUID lessonId,
      UUID utteranceId,
      byte[] boardPng,
      List<OrganizedProblem.Region> pointedRegions) {
    try (ReplyTicket ticket = dialogue.beginReply(KIND, userId, lessonId, utteranceId)) {
      OrganizedProblem problem = organizedProblemOf(userId, ticket.parent().materialId());
      StudentType student = settings.requireCompleted(userId).studentType();
      List<LanguageModel.Subtitle> subtitles = subtitlesExcept(userId, lessonId, utteranceId);
      List<LanguageModel.Region> regions =
          pointedRegions.stream().map(LessonService::toLlmRegion).toList();
      Media board = new Media(BOARD_MIME_TYPE, boardPng);
      String utterance = ticket.utteranceText();

      CompletableFuture<StudentReply> studentReply =
          CompletableFuture.supplyAsync(
              () ->
                  llm.studentReply(
                      context(userId, AiCallKind.STUDENT_REPLY),
                      new StudentReplyInput(
                          student,
                          problem.problemText(),
                          problem.studentWork(),
                          board,
                          utterance,
                          subtitles,
                          regions)),
              PARALLEL);
      CompletableFuture<Optional<Degrees>> evaluation =
          CompletableFuture.supplyAsync(
              () ->
                  evaluate(
                      userId,
                      new EvaluationInput(
                          problem.teachingPoints(),
                          problem.modelAnswer(),
                          problem.explanationSteps(),
                          problem.problemText(),
                          board,
                          utterance,
                          subtitles,
                          regions)),
              PARALLEL);

      // 失敗しても、両方が終わってから返す。途中で閉じると、評価の呼び出しの間に次の処理が入る
      CompletableFuture.allOf(studentReply, evaluation).handle((done, failed) -> null).join();
      StudentReply reply = awaitStudentReply(studentReply);
      Optional<Annotation> annotation = toAnnotation(reply.annotation());
      Expression expression = toExpression(reply.expression());
      RecordedReply recorded =
          dialogue.commitReply(
              ticket, NewTurn.student(reply.text(), expression, annotation), evaluation.join());

      DialogueLimits kindLimits = KIND.limits();
      boolean limitReached =
          kindLimits.isRoundLimitReached(recorded.roundCount())
              || kindLimits.isDeadlineReached(ticket.parent().deadlineAt(), clock.instant());
      return new Reply(
          new Turn(recorded.turnId(), reply.text(), expression),
          annotation,
          recorded.roundCount(),
          limitReached);
    }
  }

  /** 字幕の音声を返す。字幕 1 行につき、音声の生成は 1 回だけ。 */
  public byte[] audio(UUID userId, UUID lessonId, UUID turnId) {
    return dialogue.synthesizeAudio(KIND, userId, lessonId, turnId);
  }

  /**
   * チャイムの後に、まとめノートを作り、授業を記録する (仕様書 5.6)。
   *
   * <p>同じ授業の記録が既にあれば、LLM を呼ばず、保存済みの結果を返す。上限に達していなければ、記録しない。成功したら、授業の記録、理解度、模試の機会の数え、授業の行の削除を 1
   * つのトランザクションで行う。
   *
   * @param boardPng チャイムの時点のボードの画像 (PNG)
   * @throws ConflictException 上限に達していない (`limit_not_reached`)、処理中 (`busy`)
   * @throws ExternalFailureException まとめの作成が失敗した (`summary_failed`)。何も記録しない
   * @throws NotFoundException 授業が無い。LLM の呼び出しの間に中断されたときも含む
   */
  public Summary summarize(UUID userId, UUID lessonId, byte[] boardPng) {
    dialogue.requireReady(userId);
    Optional<RecordedLesson> stored = lessonRecords.find(userId, lessonId);
    if (stored.isPresent()) {
      return summaryOf(userId, stored.get());
    }
    ParentState parent =
        dialogues.findParent(KIND, userId, lessonId).orElseThrow(NotFoundException::new);
    if (!isFinished(parent)) {
      throw new ConflictException(LIMIT_NOT_REACHED_CODE, LIMIT_NOT_REACHED_MESSAGE);
    }
    try (Processing ignored = dialogue.beginProcessing(KIND, userId, lessonId)) {
      StudentType student = settings.requireCompleted(userId).studentType();
      SummaryResult result =
          summarizeWithLlm(
              userId, student, subtitles(userId, lessonId), new Media(BOARD_MIME_TYPE, boardPng));
      List<String> quotes = result.quotes() == null ? List.of() : result.quotes();
      RecordedLesson recorded =
          lessonRecords
              .complete(userId, lessonId, result.summaryText(), quotes, clock.instant())
              // 同時に確定した呼び出しがあれば、その結果を返す
              .or(() -> lessonRecords.find(userId, lessonId))
              .orElseThrow(NotFoundException::new);
      return summaryOf(userId, recorded);
    }
  }

  /** 授業を中断する。記録せず、字幕を消す。進行中の授業が無ければ何もしない。 */
  public void abandon(UUID userId, UUID lessonId) {
    dialogues.deleteParent(KIND, userId, lessonId);
  }

  /** 最初の発話。LLM が失敗したら、固定の文言にする。 */
  private Line openingLine(UUID userId, StudentType student, OrganizedProblem problem) {
    try {
      StudentReply reply =
          llm.studentReply(
              context(userId, AiCallKind.STUDENT_REPLY),
              new StudentReplyInput(
                  student,
                  problem.problemText(),
                  problem.studentWork(),
                  null,
                  null,
                  List.of(),
                  List.of()));
      if (isBlank(reply.text())) {
        return fallbackOpening();
      }
      return new Line(reply.text(), toExpression(reply.expression()));
    } catch (RuntimeException e) {
      LOG.warn("最初の発話の作成に失敗しました。固定の文言で始めます: {}", e.getClass().getName());
      return fallbackOpening();
    }
  }

  private static Line fallbackOpening() {
    return new Line(FALLBACK_OPENING, Expression.NORMAL);
  }

  /** 学習評価。失敗したら、度合いを変えないため空を返す。応答は止めない。 */
  private Optional<Degrees> evaluate(UUID userId, EvaluationInput input) {
    try {
      Evaluation evaluation = llm.evaluate(context(userId, AiCallKind.EVALUATION), input);
      return Optional.of(new Degrees(evaluation.degrees()));
    } catch (RuntimeException e) {
      LOG.warn("学習評価に失敗しました。度合いは変えません: {}", e.getClass().getName());
      return Optional.empty();
    }
  }

  /** 生徒の応答の完了を待つ。失敗と、形の不正は、どちらも 502 の失敗にする。 */
  private static StudentReply awaitStudentReply(CompletableFuture<StudentReply> future) {
    StudentReply reply;
    try {
      reply = future.join();
    } catch (CompletionException e) {
      throw studentReplyFailed(e.getCause());
    }
    if (reply == null || isBlank(reply.text()) || reply.expression() == null) {
      throw studentReplyFailed(null);
    }
    return reply;
  }

  private static ExternalFailureException studentReplyFailed(Throwable cause) {
    LOG.warn("生徒の応答に失敗しました: {}", cause == null ? "形の不正" : cause.getClass().getName());
    return new ExternalFailureException(STUDENT_REPLY_FAILED_CODE, STUDENT_REPLY_FAILED_MESSAGE);
  }

  private SummaryResult summarizeWithLlm(
      UUID userId, StudentType student, List<LanguageModel.Subtitle> subtitles, Media board) {
    SummaryResult result;
    try {
      result =
          llm.summarize(
              context(userId, AiCallKind.SUMMARY), new SummaryInput(student, subtitles, board));
    } catch (RuntimeException e) {
      LOG.warn("まとめの作成に失敗しました: {}", e.getClass().getName());
      throw summaryFailed();
    }
    if (result == null || isBlank(result.summaryText())) {
      throw summaryFailed();
    }
    return result;
  }

  private static ExternalFailureException summaryFailed() {
    return new ExternalFailureException(SUMMARY_FAILED_CODE, SUMMARY_FAILED_MESSAGE);
  }

  /** 上限に達しているか、期限まで {@link #SUMMARY_GRACE} 以内であれば、まとめを受け付ける。 */
  private boolean isFinished(ParentState parent) {
    boolean roundLimit = KIND.limits().isRoundLimitReached(parent.roundCount());
    boolean nearDeadline = !clock.instant().plus(SUMMARY_GRACE).isBefore(parent.deadlineAt());
    return roundLimit || nearDeadline;
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

  /** 利用者の授業の字幕。発話の対象 (`excluded`) は除く。 */
  private List<LanguageModel.Subtitle> subtitlesExcept(UUID userId, UUID lessonId, UUID excluded) {
    return dialogues.listSubtitles(KIND, userId, lessonId).stream()
        .filter(subtitle -> !subtitle.id().equals(excluded))
        .map(LessonService::toLlmSubtitle)
        .toList();
  }

  private List<LanguageModel.Subtitle> subtitles(UUID userId, UUID lessonId) {
    return dialogues.listSubtitles(KIND, userId, lessonId).stream()
        .map(LessonService::toLlmSubtitle)
        .toList();
  }

  private Summary summaryOf(UUID userId, RecordedLesson recorded) {
    WeekRange week = WeekRange.containing(clock.instant(), clock.getZone());
    return new Summary(
        recorded.id(),
        recorded.completedAt(),
        recorded.summaryText(),
        recorded.quotes(),
        lessonRecords.countCompletedIn(userId, week));
  }

  private static LanguageModel.Subtitle toLlmSubtitle(Subtitle subtitle) {
    return new LanguageModel.Subtitle(
        LanguageModel.Speaker.valueOf(subtitle.speaker().name()), subtitle.body());
  }

  /** 生徒の印は、範囲が外れていれば無いものとする。 */
  private static Optional<Annotation> toAnnotation(LanguageModel.StudentAnnotation annotation) {
    if (annotation == null || annotation.kind() == null || annotation.region() == null) {
      return Optional.empty();
    }
    OrganizedProblem.Region region = fromLlmRegion(annotation.region());
    if (!region.fitsInImage()) {
      return Optional.empty();
    }
    return Optional.of(new Annotation(Annotation.Kind.valueOf(annotation.kind().name()), region));
  }

  private static Expression toExpression(LanguageModel.StudentExpression expression) {
    return Expression.valueOf(expression.name());
  }

  private static LanguageModel.Region toLlmRegion(OrganizedProblem.Region region) {
    return new LanguageModel.Region(region.x(), region.y(), region.width(), region.height());
  }

  private static OrganizedProblem.Region fromLlmRegion(LanguageModel.Region region) {
    return new OrganizedProblem.Region(region.x(), region.y(), region.width(), region.height());
  }

  private static AiCallContext context(UUID userId, AiCallKind kind) {
    return new AiCallContext(userId, kind);
  }

  private static boolean isBlank(String text) {
    return text == null || text.isBlank();
  }

  private static Thread daemonThread(Runnable runnable) {
    Thread thread = new Thread(runnable, "teachme-lesson");
    // 作業が残っていても、プロセスの終了を妨げない
    thread.setDaemon(true);
    return thread;
  }

  /** 最初の発話の文章と表情。 */
  private record Line(String text, Expression expression) {}
}
