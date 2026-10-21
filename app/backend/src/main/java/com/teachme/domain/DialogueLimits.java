package com.teachme.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * 授業と相談の往復の上限と、時間の上限 (NFR-201、アーキテクチャ設計 4.2)。
 *
 * <p>往復の上限は変えられない。時間は、テストの上書き (`PUT /api/test/limits`) で短くできる。時刻は呼び出し側が渡す。
 *
 * @param maxRounds 往復の上限
 * @param duration 開始から期限までの時間
 */
public record DialogueLimits(int maxRounds, Duration duration) {

  /** 時間の上書きの範囲 (秒)。openapi.yaml の `PUT /test/limits` と合わせる。 */
  public static final int MIN_SECONDS = 1;

  public static final int MAX_SECONDS = 600;

  public DialogueLimits {
    if (maxRounds < 1) {
      throw new IllegalArgumentException("往復の上限は 1 以上にしてください");
    }
    if (duration.isNegative() || duration.isZero()) {
      throw new IllegalArgumentException("時間の上限は正の値にしてください");
    }
  }

  /** 授業: 20 往復、600 秒。 */
  public static DialogueLimits lesson() {
    return new DialogueLimits(20, Duration.ofSeconds(MAX_SECONDS));
  }

  /** 先輩先生への相談: 15 往復、600 秒。 */
  public static DialogueLimits consultation() {
    return new DialogueLimits(15, Duration.ofSeconds(MAX_SECONDS));
  }

  /**
   * 時間だけを上書きした上限を返す。往復の上限は変わらない。
   *
   * @throws IllegalArgumentException 1 から 600 秒の範囲の外のとき
   */
  public DialogueLimits withSeconds(int seconds) {
    if (seconds < MIN_SECONDS || seconds > MAX_SECONDS) {
      throw new IllegalArgumentException("時間は 1 から 600 秒の範囲で指定してください");
    }
    return new DialogueLimits(maxRounds, Duration.ofSeconds(seconds));
  }

  /** 開始の時刻から、期限の時刻を求める。 */
  public Instant deadlineAt(Instant startedAt) {
    return startedAt.plus(duration);
  }

  /** 往復が上限に達したか。 */
  public boolean isRoundLimitReached(int roundCount) {
    return roundCount >= maxRounds;
  }

  /** 期限に達したか (期限の時刻ちょうどを含む)。 */
  public boolean isDeadlineReached(Instant deadlineAt, Instant now) {
    return !now.isBefore(deadlineAt);
  }

  /** 発話や応答を受け付けてよいか。往復が上限に達しておらず、期限の前であるときだけ真になる (4.4)。 */
  public boolean canAccept(int roundCount, Instant deadlineAt, Instant now) {
    return !isRoundLimitReached(roundCount) && !isDeadlineReached(deadlineAt, now);
  }
}
