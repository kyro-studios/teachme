package com.teachme.application;

import com.teachme.ai.AiCallContext;
import com.teachme.ai.AiCallKind;
import com.teachme.ai.AiFailedException;
import com.teachme.ai.Media;
import com.teachme.ai.SpeechToText;
import com.teachme.ai.TextToSpeech;
import com.teachme.ai.TextToSpeech.Voice;
import com.teachme.domain.Degrees;
import com.teachme.domain.DialogueKind;
import com.teachme.domain.Speaker;
import com.teachme.persistence.DialogueRepository;
import com.teachme.persistence.DialogueRepository.NewTurn;
import com.teachme.persistence.DialogueRepository.ParentState;
import com.teachme.persistence.DialogueRepository.RecordedReply;
import com.teachme.persistence.DialogueRepository.TurnRow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 授業と相談が共有する、発話、応答、音声の共通部品 (アーキテクチャ設計 4.4)。
 *
 * <p>処理中の印 (`busy_since`) で、同じ対話の発話と応答は、同時に 1 件だけ進む。往復と期限の上限は、発話と応答の受け付けの時点だけ見る。 種類に固有の部分 (LLM
 * の呼び出しと、度合いの計算) は、呼び出し側が、{@link #beginReply} と {@link #commitReply} の間で行う。
 */
public final class DialogueService {

  static final String BUSY_CODE = "busy";
  static final String LIMIT_REACHED_CODE = "limit_reached";
  static final String ALREADY_REPLIED_CODE = "already_replied";
  static final String UTTERANCE_NOT_FOUND_CODE = "utterance_not_found";
  static final String PRIVACY_NOT_CONFIRMED_CODE = "privacy_not_confirmed";
  static final String TTS_FAILED_CODE = "tts_failed";

  private static final String BUSY_MESSAGE = "処理中です。少し待ってから、もう一度お試しください";
  private static final String LIMIT_REACHED_MESSAGE = "上限に達したため、これ以上は話せません";
  private static final String ALREADY_REPLIED_MESSAGE = "この発話には、もう応答しています";
  private static final String UTTERANCE_NOT_FOUND_MESSAGE = "この発話には応答できません";
  private static final String PRIVACY_NOT_CONFIRMED_MESSAGE = "初期セットアップで、個人情報の確認にチェックを入れてください";
  private static final String TTS_FAILED_MESSAGE = "音声を作れませんでした";

  /** 処理中の印の有効時間。これより古い印は、異常終了の名残として無視する (アーキテクチャ設計 4.4)。 */
  static final Duration BUSY_TIMEOUT = Duration.ofSeconds(90);

  private final SettingsService settings;
  private final DialogueRepository dialogues;
  private final SpeechToText speechToText;
  private final TextToSpeech textToSpeech;
  private final Clock clock;

  public DialogueService(
      SettingsService settings,
      DialogueRepository dialogues,
      SpeechToText speechToText,
      TextToSpeech textToSpeech,
      Clock clock) {
    this.settings = settings;
    this.dialogues = dialogues;
    this.speechToText = speechToText;
    this.textToSpeech = textToSpeech;
    this.clock = clock;
  }

  /**
   * 認識できた発話。
   *
   * @param utteranceId 入れた利用者の字幕の識別子
   * @param text 認識の結果 (前後の空白を除いたもの)
   */
  public record Recognition(UUID utteranceId, String text) {}

  /**
   * 録音。`web` が、形式と大きさを検査してから作る。
   *
   * @param mimeType 録音の形式 (webm、mp4、wav のいずれか)
   * @param bytes 録音の中身
   */
  public record Recording(String mimeType, byte[] bytes) {

    /** 録音の大きさの上限 (5 MB)。60 秒の録音が入る大きさ。 */
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    public Recording {
      Objects.requireNonNull(mimeType, "mimeType");
      Objects.requireNonNull(bytes, "bytes");
      if (bytes.length == 0 || bytes.length > MAX_BYTES) {
        throw new IllegalArgumentException("録音は 1 バイト以上 5 MB 以下にしてください");
      }
    }
  }

  /**
   * 録音を文章にし、認識できたら利用者の字幕として入れる。応答は作らない。
   *
   * <p>往復か期限に達していれば、STT を呼ばずに、録音を捨てて 409 `limit_reached` を返す。STT の失敗と、認識結果が空のときは、何も入れず、空を返す
   * (往復にも数えない)。どちらの場合も、STT の後に親の行が無ければ 404 を返す。
   *
   * @return 認識できたときの字幕。認識できなかったとき空
   * @throws ConflictException 設定が未完了、個人情報の確認が無い、処理中、往復か期限に達している
   * @throws NotFoundException 親の行が無い。STT の後に中断されたときも含む
   */
  public Optional<Recognition> acceptUtterance(
      DialogueKind kind, UUID userId, UUID parentId, Recording recording) {
    requireReady(userId);
    Instant busySince = beginBusy(kind, userId, parentId);
    try {
      checkLimit(kind, requireParent(kind, userId, parentId));
      String text = recognize(userId, recording);
      if (text.isEmpty()) {
        requireParent(kind, userId, parentId);
        return Optional.empty();
      }
      UUID utteranceId =
          dialogues
              .recordUtterance(kind, userId, parentId, text)
              .orElseThrow(NotFoundException::new);
      return Optional.of(new Recognition(utteranceId, text));
    } finally {
      dialogues.releaseBusy(kind, parentId, busySince);
    }
  }

  /**
   * 応答を始める。発話が未応答の最後の利用者の字幕であること、往復と期限の上限を確かめ、処理中の印を立てる。
   *
   * <p>返す {@link ReplyTicket} は、応答が終わるまで開いたままにする。閉じると、印が外れる。
   *
   * @throws ConflictException 設定が未完了、個人情報の確認が無い、処理中、応答済み (`already_replied`)、置き換えられた、または無い発話
   *     (`utterance_not_found`)、往復か期限に達している
   * @throws NotFoundException 親の行が無い
   */
  public ReplyTicket beginReply(DialogueKind kind, UUID userId, UUID parentId, UUID utteranceId) {
    requireReady(userId);
    Instant busySince = beginBusy(kind, userId, parentId);
    try {
      ParentState parent = requireParent(kind, userId, parentId);
      TurnRow utterance =
          dialogues
              .findTurn(kind, userId, parentId, utteranceId)
              .filter(turn -> turn.speaker() == Speaker.USER)
              .orElseThrow(
                  () ->
                      new ConflictException(UTTERANCE_NOT_FOUND_CODE, UTTERANCE_NOT_FOUND_MESSAGE));
      if (utterance.replied()) {
        throw new ConflictException(ALREADY_REPLIED_CODE, ALREADY_REPLIED_MESSAGE);
      }
      checkLimit(kind, parent);
      return new ReplyTicket(kind, userId, parentId, utteranceId, utterance.body(), busySince);
    } catch (RuntimeException e) {
      dialogues.releaseBusy(kind, parentId, busySince);
      throw e;
    }
  }

  /**
   * 応答を確定する。応答の字幕を入れ、利用者の字幕を応答済みにし、往復を 1 つ増やす。`evaluated` があれば (授業のみ)、度合いを最大値で取り込む。
   *
   * <p>LLM の呼び出しの後に行う。親の行が無ければ (中断された後なら) 結果を捨てて 404 を返す。確定の後も印は残るため、呼び出し側は {@link ReplyTicket}
   * を閉じる。
   *
   * @return 入れた応答の字幕の識別子 (API の `turn.turnId`、音声の取得に使う) と、増やした後の往復の数
   * @throws NotFoundException 親の行が無い
   */
  public RecordedReply commitReply(ReplyTicket ticket, NewTurn reply, Optional<Degrees> evaluated) {
    ticket.requireOpen();
    return dialogues
        .recordReply(
            ticket.kind(),
            ticket.userId(),
            ticket.parentId(),
            ticket.utteranceId(),
            reply,
            evaluated)
        .orElseThrow(NotFoundException::new);
  }

  /**
   * 字幕の音声を返す。字幕 1 行につき、音声の生成は最大 1 回。生成済みなら、保存したものを返す。
   *
   * <p>生成の前に `audio_attempted` を立てる。失敗したら、同じ字幕では二度と生成せず、502 `tts_failed` を返す (字幕だけで続ける)。
   *
   * @throws NotFoundException 字幕が無い、または利用者の字幕である (利用者の字幕には音声を作らない)
   * @throws ExternalFailureException 音声の生成に失敗した、または生成中である
   */
  public byte[] synthesizeAudio(DialogueKind kind, UUID userId, UUID parentId, UUID turnId) {
    TurnRow turn =
        dialogues.findTurn(kind, userId, parentId, turnId).orElseThrow(NotFoundException::new);
    if (turn.speaker() == Speaker.USER) {
      throw new NotFoundException();
    }
    if (turn.audio() != null) {
      return turn.audio();
    }
    if (turn.audioAttempted() || !dialogues.markAudioAttempted(turnId)) {
      return storedAudio(kind, userId, parentId, turnId);
    }
    Voice voice = voiceOf(userId, turn.speaker());
    try {
      Media audio =
          textToSpeech.synthesize(new AiCallContext(userId, AiCallKind.TTS), voice, turn.body());
      dialogues.saveAudio(turnId, audio.bytes());
      return audio.bytes();
    } catch (AiFailedException e) {
      throw ttsFailed();
    }
  }

  /** 設定が完了し、個人情報の確認が済んでいることを確かめる。 */
  private void requireReady(UUID userId) {
    if (!settings.requireCompleted(userId).privacyConfirmed()) {
      throw new ConflictException(PRIVACY_NOT_CONFIRMED_CODE, PRIVACY_NOT_CONFIRMED_MESSAGE);
    }
  }

  /**
   * 処理中の印を立てる。立てられなければ、親の行の有無を見て、無ければ 404、あれば 409 `busy`。
   *
   * @return 印の時刻。{@link DialogueRepository#releaseBusy} に渡す
   */
  private Instant beginBusy(DialogueKind kind, UUID userId, UUID parentId) {
    Instant busySince = clock.instant().truncatedTo(ChronoUnit.MICROS);
    if (dialogues.markBusy(kind, userId, parentId, busySince, busySince.minus(BUSY_TIMEOUT))) {
      return busySince;
    }
    requireParent(kind, userId, parentId);
    throw new ConflictException(BUSY_CODE, BUSY_MESSAGE);
  }

  private ParentState requireParent(DialogueKind kind, UUID userId, UUID parentId) {
    return dialogues.findParent(kind, userId, parentId).orElseThrow(NotFoundException::new);
  }

  /** 往復が上限に達しているか、期限を過ぎていれば、409 `limit_reached`。 */
  private void checkLimit(DialogueKind kind, ParentState parent) {
    if (!kind.limits().canAccept(parent.roundCount(), parent.deadlineAt(), clock.instant())) {
      throw new ConflictException(LIMIT_REACHED_CODE, LIMIT_REACHED_MESSAGE);
    }
  }

  /** 録音を文章にする。失敗と空の結果は、どちらも空の文字列にする (認識できなかった)。 */
  private String recognize(UUID userId, Recording recording) {
    try {
      String text =
          speechToText.transcribe(
              new AiCallContext(userId, AiCallKind.STT),
              new Media(recording.mimeType(), recording.bytes()));
      return text == null ? "" : text.strip();
    } catch (AiFailedException e) {
      return "";
    }
  }

  private byte[] storedAudio(DialogueKind kind, UUID userId, UUID parentId, UUID turnId) {
    TurnRow turn =
        dialogues.findTurn(kind, userId, parentId, turnId).orElseThrow(NotFoundException::new);
    if (turn.audio() == null) {
      throw ttsFailed();
    }
    return turn.audio();
  }

  private Voice voiceOf(UUID userId, Speaker speaker) {
    return switch (speaker) {
      case STUDENT ->
          new Voice.Student(
              settings.find(userId).orElseThrow(NotFoundException::new).studentType());
      case MENTOR -> new Voice.Mentor();
      case USER -> throw new IllegalStateException("利用者の字幕には音声を作らない");
    };
  }

  private static ExternalFailureException ttsFailed() {
    return new ExternalFailureException(TTS_FAILED_CODE, TTS_FAILED_MESSAGE);
  }

  /**
   * 応答の途中であることを表す札。処理中の印を持ち、閉じると印が外れる。
   *
   * <p>応答の LLM の呼び出しが失敗しても、閉じれば、往復は増えず、利用者の字幕は残る。
   */
  public final class ReplyTicket implements AutoCloseable {

    private final DialogueKind kind;
    private final UUID userId;
    private final UUID parentId;
    private final UUID utteranceId;
    private final String utteranceText;
    private final Instant busySince;
    private boolean open = true;

    private ReplyTicket(
        DialogueKind kind,
        UUID userId,
        UUID parentId,
        UUID utteranceId,
        String utteranceText,
        Instant busySince) {
      this.kind = kind;
      this.userId = userId;
      this.parentId = parentId;
      this.utteranceId = utteranceId;
      this.utteranceText = utteranceText;
      this.busySince = busySince;
    }

    public DialogueKind kind() {
      return kind;
    }

    public UUID parentId() {
      return parentId;
    }

    /** 応答の対象の、利用者の字幕の識別子。 */
    public UUID utteranceId() {
      return utteranceId;
    }

    /** 応答の対象の、利用者の発話の文章。LLM に渡す。 */
    public String utteranceText() {
      return utteranceText;
    }

    private UUID userId() {
      return userId;
    }

    private void requireOpen() {
      if (!open) {
        throw new IllegalStateException("閉じた応答の札は使えない");
      }
    }

    @Override
    public void close() {
      if (open) {
        open = false;
        dialogues.releaseBusy(kind, parentId, busySince);
      }
    }
  }
}
