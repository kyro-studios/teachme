package com.teachme.testsupport;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** 非同期の処理 (教材の整理のワーカー) の結果を待つ。時間を過ぎたら失敗にする。 */
public final class Await {
  private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);
  private static final long POLL_MILLIS = 20;

  private Await() {}

  /**
   * @throws AssertionError 10 秒以内に条件が成り立たなかったとき
   */
  public static void until(String description, BooleanSupplier condition)
      throws InterruptedException {
    long deadline = System.nanoTime() + TIMEOUT_NANOS;
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        fail("時間内に次の状態になりませんでした: " + description);
      }
      Thread.sleep(POLL_MILLIS);
    }
  }
}
