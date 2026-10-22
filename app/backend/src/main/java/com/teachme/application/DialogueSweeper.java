package com.teachme.application;

import com.teachme.domain.DialogueKind;
import com.teachme.persistence.DialogueRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 進行中の授業と相談の掃除 (アーキテクチャ設計 4.4)。開始から {@link #MAX_AGE} を過ぎた行を消す。中断の通知が届かなかった場合の保険。
 *
 * <p>起動時と、{@link #INTERVAL} ごとに掃除する。
 */
public final class DialogueSweeper {

  /** 開始から、この時間を過ぎた授業と相談を消す。 */
  static final Duration MAX_AGE = Duration.ofMinutes(60);

  /** 掃除の間隔。 */
  static final Duration INTERVAL = Duration.ofMinutes(10);

  private static final Logger LOG = LoggerFactory.getLogger(DialogueSweeper.class);

  private final DialogueRepository dialogues;
  private final Clock clock;
  private final ScheduledExecutorService sweeper =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "teachme-dialogue-sweep");
            // 掃除が残っていても、プロセスの終了を妨げない
            thread.setDaemon(true);
            return thread;
          });

  public DialogueSweeper(DialogueRepository dialogues, Clock clock) {
    this.dialogues = dialogues;
    this.clock = clock;
  }

  /** 起動時の掃除を行い、以後 {@link #INTERVAL} ごとに掃除する。 */
  public void start() {
    sweeper.scheduleAtFixedRate(this::sweep, 0, INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
  }

  /** 掃除を止める。 */
  public void close() {
    sweeper.shutdownNow();
  }

  /**
   * 開始から {@link #MAX_AGE} を過ぎた、授業と相談を消す。
   *
   * @return 消した件数
   */
  int sweep() {
    try {
      Instant threshold = clock.instant().minus(MAX_AGE);
      int count = 0;
      for (DialogueKind kind : DialogueKind.values()) {
        count += dialogues.deleteStartedBefore(kind, threshold);
      }
      if (count > 0) {
        LOG.info("古い授業と相談を消しました: {} 件", count);
      }
      return count;
    } catch (RuntimeException e) {
      // 次の掃除で、また試す。例外を出すと、定期の実行が止まるため
      LOG.error("授業と相談の掃除に失敗しました: {}", e.getClass().getName());
      return 0;
    }
  }
}
