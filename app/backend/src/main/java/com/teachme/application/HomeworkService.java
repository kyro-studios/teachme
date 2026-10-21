package com.teachme.application;

import com.teachme.ai.AiCallContext;
import com.teachme.ai.AiCallKind;
import com.teachme.ai.AiFailedException;
import com.teachme.ai.LanguageModel;
import com.teachme.ai.LanguageModel.HomeworkJudgement;
import com.teachme.ai.LanguageModel.HomeworkVerdict;
import com.teachme.ai.Media;
import com.teachme.domain.ImageFormat;
import com.teachme.domain.TodayMode;
import com.teachme.persistence.TodayModeRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * 宿題の丸つけ (仕様書 5.2)。写真は保存せず、判定の結果も保存しない。
 *
 * <p>写真の形式と大きさは、`web` が検査してから渡す。
 */
public final class HomeworkService {

  private static final String MODE_NOT_HOMEWORK_CHECK_CODE = "mode_not_homework_check";
  private static final String MODE_NOT_HOMEWORK_CHECK_MESSAGE = "今日は宿題の丸つけの日ではありません";

  private final SettingsService settings;
  private final TodayModeRepository todayModes;
  private final LanguageModel languageModel;
  private final Clock clock;

  public HomeworkService(
      SettingsService settings,
      TodayModeRepository todayModes,
      LanguageModel languageModel,
      Clock clock) {
    this.settings = settings;
    this.todayModes = todayModes;
    this.languageModel = languageModel;
    this.clock = clock;
  }

  /**
   * 宿題の写真を判定する。外部 AI の失敗は、判定できなかった (`UNDETERMINED`) として返す。
   *
   * @throws ConflictException 設定が未完了のとき (`setup_required`)、今日のモードが宿題の丸つけでないとき
   *     (`mode_not_homework_check`)
   */
  public Result check(UUID userId, ImageFormat format, byte[] photo) {
    settings.requireCompleted(userId);
    TodayMode mode = todayModes.effectiveMode(userId, LocalDate.now(clock));
    if (mode != TodayMode.HOMEWORK_CHECK) {
      throw new ConflictException(MODE_NOT_HOMEWORK_CHECK_CODE, MODE_NOT_HOMEWORK_CHECK_MESSAGE);
    }
    try {
      HomeworkJudgement judgement =
          languageModel.gradeHomework(
              new AiCallContext(userId, AiCallKind.HOMEWORK),
              new Media(format.mimeType(), Objects.requireNonNull(photo, "photo")));
      return new Result(
          verdictOf(judgement.verdict()), Objects.requireNonNullElse(judgement.comment(), ""));
    } catch (AiFailedException e) {
      // 写真と応答の中身は、ログにも出さない。失敗は、判定できなかった結果として返す (仕様書 8 節)
      return new Result(Verdict.UNDETERMINED, "");
    }
  }

  private static Verdict verdictOf(HomeworkVerdict verdict) {
    return switch (verdict) {
      case CORRECT -> Verdict.CORRECT;
      case INCORRECT -> Verdict.INCORRECT;
      case UNDETERMINED -> Verdict.UNDETERMINED;
    };
  }

  /** 判定。`wireName` は openapi.yaml の `HomeworkResult.verdict` と同じ値。 */
  public enum Verdict {
    CORRECT("correct"),
    INCORRECT("incorrect"),
    UNDETERMINED("undetermined");

    private final String wireName;

    Verdict(String wireName) {
      this.wireName = wireName;
    }

    public String wireName() {
      return wireName;
    }
  }

  /**
   * 判定の結果。
   *
   * @param comment 一言のコメント。判定できなかったときは空
   */
  public record Result(Verdict verdict, String comment) {}
}
