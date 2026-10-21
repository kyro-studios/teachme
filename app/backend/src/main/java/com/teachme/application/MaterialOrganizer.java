package com.teachme.application;

import com.teachme.ai.AiCallContext;
import com.teachme.ai.AiCallKind;
import com.teachme.ai.LanguageModel;
import com.teachme.ai.LanguageModel.OrganizeResult;
import com.teachme.ai.Media;
import com.teachme.domain.OrganizedProblem;
import com.teachme.persistence.MaterialRepository;
import com.teachme.persistence.MaterialRepository.StoredImage;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 教材の整理 (仕様書 5.3、アーキテクチャ設計 4.5)。登録や「やり直す」の後に、同じプロセスの固定 2 スレッドで LLM を呼ぶ。
 *
 * <p>整理が失敗したとき、または指導のポイントか図表の形が正しくないときは、1 回だけやり直す (LLM の呼び出しは 1 件あたり 2 回まで)。 状態は `organizing`
 * のときだけ更新する。整理の途中で教材が消えたときは、結果を捨てる。
 *
 * <p>起動時と、{@link #SWEEP_INTERVAL} ごとに、{@link #STALE_AFTER} を過ぎた整理中の教材を、整理できませんでした (`failed`)
 * にする。インスタンスの停止で整理が途切れた場合の保険。
 */
public final class MaterialOrganizer {

  static final int WORKER_THREADS = 2;

  /** 1 回の整理での、LLM の呼び出しの上限 (最初の 1 回と、自動の 1 回だけのやり直し)。 */
  static final int MAX_ATTEMPTS = 2;

  /** 整理が途切れたとみなすまでの時間。 */
  static final Duration STALE_AFTER = Duration.ofMinutes(10);

  /** 掃除の間隔。 */
  static final Duration SWEEP_INTERVAL = Duration.ofMinutes(10);

  private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(10);
  private static final Logger LOG = LoggerFactory.getLogger(MaterialOrganizer.class);

  private final MaterialRepository materials;
  private final LanguageModel languageModel;
  private final Clock clock;
  // スレッドは、最初に仕事を依頼したときに作られる。組み立てただけでは、スレッドを使わない
  private final ExecutorService workers =
      Executors.newFixedThreadPool(WORKER_THREADS, daemonThreads("teachme-organizer"));
  private final ScheduledExecutorService sweeper =
      Executors.newSingleThreadScheduledExecutor(daemonThreads("teachme-organizer-sweep"));

  public MaterialOrganizer(MaterialRepository materials, LanguageModel languageModel, Clock clock) {
    this.materials = materials;
    this.languageModel = languageModel;
    this.clock = clock;
  }

  /** 起動時の掃除を行い、以後 {@link #SWEEP_INTERVAL} ごとに掃除する。 */
  public void start() {
    sweeper.scheduleAtFixedRate(
        this::sweepStale, 0, SWEEP_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
  }

  /**
   * 整理を依頼する。画像と、状態 `organizing` は、依頼の前に保存しておく。
   *
   * @param userId 整理を依頼した利用者 (ログと、AI の呼び出しの文脈に使う)
   */
  public void enqueue(UUID userId, UUID materialId) {
    workers.execute(() -> organize(userId, materialId));
  }

  /** ワーカーと掃除を止める。進行中の整理は、待つ時間の上限まで待つ。 */
  public void close() {
    sweeper.shutdownNow();
    workers.shutdownNow();
    try {
      workers.awaitTermination(SHUTDOWN_WAIT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** 1 件の教材を整理する。教材が消えていたら、何もしない。 */
  void organize(UUID userId, UUID materialId) {
    try {
      List<StoredImage> images = materials.loadImages(materialId);
      if (images.isEmpty()) {
        // 依頼のあとに、教材が削除された。外部 AI は呼ばない
        return;
      }
      List<Media> media =
          images.stream().map(image -> new Media(image.contentType(), image.data())).toList();
      Optional<OrganizedProblem> problem = Optional.empty();
      for (int attempt = 1; attempt <= MAX_ATTEMPTS && problem.isEmpty(); attempt++) {
        problem = organizeOnce(userId, media);
      }
      if (problem.isPresent()) {
        // 整理中でなければ、教材が消えたか、掃除で失敗になっているため、結果を捨てる
        materials.markOrganized(materialId, problem.get());
      } else {
        materials.markFailed(materialId);
      }
    } catch (RuntimeException e) {
      // 教材の状態は整理中のまま残り、掃除で失敗になる。入力や応答の中身は出さない
      LOG.error("教材の整理が途中で止まりました: {}", e.getClass().getName());
    }
  }

  /** 整理を 1 回呼び、検査する。失敗と、形の不正は空にする。 */
  private Optional<OrganizedProblem> organizeOnce(UUID userId, List<Media> images) {
    try {
      OrganizeResult result =
          languageModel.organize(new AiCallContext(userId, AiCallKind.ORGANIZE), images);
      return OrganizedProblem.parse(
          result.problemText(),
          toFigures(result.figures()),
          result.modelAnswer(),
          result.explanationSteps(),
          result.studentWork(),
          result.teachingPoints(),
          images.size());
    } catch (RuntimeException e) {
      // 外部 AI の失敗と、応答の形の不正を、同じく失敗として扱う。中身は出さない
      LOG.warn("教材の整理に失敗しました: {}", e.getClass().getName());
      return Optional.empty();
    }
  }

  /**
   * 開始から {@link #STALE_AFTER} を過ぎた、整理中の教材を、整理できませんでした (`failed`) にする。
   *
   * @return 失敗にした件数
   */
  int sweepStale() {
    try {
      int count = materials.failOrganizingStartedBefore(clock.instant().minus(STALE_AFTER));
      if (count > 0) {
        LOG.warn("途切れた教材の整理を失敗にしました: {} 件", count);
      }
      return count;
    } catch (RuntimeException e) {
      LOG.error("教材の整理の掃除に失敗しました: {}", e.getClass().getName());
      return 0;
    }
  }

  private static List<OrganizedProblem.Figure> toFigures(List<LanguageModel.Figure> figures) {
    if (figures == null) {
      return null;
    }
    return figures.stream()
        .map(
            figure ->
                new OrganizedProblem.Figure(
                    figure.imagePosition(),
                    new OrganizedProblem.Region(
                        figure.region().x(),
                        figure.region().y(),
                        figure.region().width(),
                        figure.region().height())))
        .toList();
  }

  private static ThreadFactory daemonThreads(String prefix) {
    AtomicInteger count = new AtomicInteger();
    return runnable -> {
      Thread thread = new Thread(runnable, prefix + "-" + count.incrementAndGet());
      // 作業が残っていても、プロセスの終了を妨げない
      thread.setDaemon(true);
      return thread;
    };
  }
}
