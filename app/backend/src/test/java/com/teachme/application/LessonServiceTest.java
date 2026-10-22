package com.teachme.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.teachme.ai.AiCallContext;
import com.teachme.ai.AiCallKind;
import com.teachme.ai.LanguageModel;
import com.teachme.ai.LanguageModel.Evaluation;
import com.teachme.ai.LanguageModel.EvaluationInput;
import com.teachme.ai.LanguageModel.HomeworkJudgement;
import com.teachme.ai.LanguageModel.MentorReply;
import com.teachme.ai.LanguageModel.MentorReplyInput;
import com.teachme.ai.LanguageModel.MockExamInput;
import com.teachme.ai.LanguageModel.MockExamResult;
import com.teachme.ai.LanguageModel.OrganizeResult;
import com.teachme.ai.LanguageModel.StudentReply;
import com.teachme.ai.LanguageModel.StudentReplyInput;
import com.teachme.ai.LanguageModel.SummaryInput;
import com.teachme.ai.LanguageModel.SummaryResult;
import com.teachme.ai.Media;
import com.teachme.ai.ScriptedResponse;
import com.teachme.ai.fake.FakeAi;
import com.teachme.application.DialogueService.Recording;
import com.teachme.application.LessonService.Reply;
import com.teachme.application.LessonService.Started;
import com.teachme.application.LessonService.Summary;
import com.teachme.application.SettingsService.SettingsCommand;
import com.teachme.domain.DialogueLimits;
import com.teachme.domain.Expression;
import com.teachme.domain.OrganizedProblem;
import com.teachme.domain.StudentType;
import com.teachme.domain.Subject;
import com.teachme.domain.TodayMode;
import com.teachme.domain.UnitName;
import com.teachme.persistence.LessonRecordRepository.RecordedLesson;
import com.teachme.persistence.MaterialRepository.NewImage;
import com.teachme.persistence.Repositories;
import com.teachme.testsupport.DialogueFixtures;
import com.teachme.testsupport.Fixtures;
import com.teachme.testsupport.MutableClock;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 授業の統合テスト。DB は Testcontainers の PostgreSQL を使い、外部 AI は {@link FakeAi} で置き換える。 */
class LessonServiceTest {
  private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final Instant START = Instant.parse("2026-10-21T01:00:00Z");
  private static final Recording RECORDING = new Recording("audio/webm", new byte[] {1, 2, 3});
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};
  private static final String PROBLEM = "y = x^2 - 4x + 3 の頂点を求めなさい";
  private static final List<String> POINTS = List.of("平方完成をする", "頂点を読む", "符号に注意する");
  private static final List<String> STEPS = List.of("平方完成の形を作る");

  /** 授業の上限の時間の、まとめが受け付けられる直前 (期限の 3 秒前まで)。 */
  private static final Duration NEAR_DEADLINE = Duration.ofSeconds(598);

  private DataSource dataSource;
  private Repositories repositories;
  private MutableClock clock;
  private SettingsService settings;
  private FakeAi fakeAi;
  private LessonService lessons;
  private UUID unitId;
  private UUID materialId;

  @BeforeEach
  void setUp() {
    dataSource = Fixtures.freshDataSource();
    repositories = new Repositories(dataSource);
    clock = new MutableClock(START, Fixtures.JAPAN);
    settings = new SettingsService(repositories.settings(), clock);
    fakeAi = new FakeAi();
    settings.save(USER, command("二次関数"));
    settings.save(OTHER, command("三角比"));
    lessons = lessonsWith(fakeAi);
    unitId = settings.find(USER).orElseThrow().unitId();
    materialId = organizedMaterial(unitId);
  }

  /** 外部 AI を差し替えた授業のサービス。他は、setUp の授業のサービスと同じ部品を使う。 */
  private LessonService lessonsWith(LanguageModel llm) {
    DialogueService dialogue =
        new DialogueService(settings, repositories.dialogues(), fakeAi, fakeAi, clock);
    return new LessonService(
        settings,
        dialogue,
        repositories.dialogues(),
        repositories.materials(),
        repositories.todayModes(),
        repositories.lessonRecords(),
        llm,
        id -> DialogueLimits.lesson(),
        clock);
  }

  private static SettingsCommand command(String unitName) {
    return new SettingsCommand(
        StudentType.LOGICAL, Subject.MATH, UnitName.parse(unitName).orElseThrow(), 3, true);
  }

  /** 整理済みの教材を 1 件入れる。整理の結果は、問題文、生徒の途中の解答、指導のポイント 3 件を持つ。 */
  private UUID organizedMaterial(UUID unit) {
    UUID id = UUID.randomUUID();
    repositories
        .materials()
        .insertOrganizing(id, unit, List.of(new NewImage("image/png", PNG)), clock.instant());
    repositories.materials().markOrganized(id, problem());
    return id;
  }

  private static OrganizedProblem problem() {
    return OrganizedProblem.parse(
            PROBLEM, List.of(), "頂点は (2, -1)", STEPS, "y = (x - 2)^2 と書いて手が止まっている", POINTS, 1)
        .orElseThrow();
  }

  /** 得点の判定を、指導のポイント 3 件の度合いで決めた応答。 */
  private static ScriptedResponse evaluation(int first, int second, int third) {
    return new ScriptedResponse(
        true, Map.<String, Object>of("degrees", List.of(first, second, third)));
  }

  private UUID startLesson() {
    return lessons.start(USER, materialId).lessonId();
  }

  /** 発話を 1 件受け付け、その字幕の識別子を返す。 */
  private UUID utter(UUID lessonId) {
    return lessons.utter(USER, lessonId, RECORDING).orElseThrow().utteranceId();
  }

  private Reply reply(UUID lessonId, UUID utteranceId) {
    return lessons.reply(USER, lessonId, utteranceId, PNG, List.of());
  }

  /** 発話 1 回と、その応答 1 回。 */
  private Reply roundTrip(UUID lessonId) {
    return reply(lessonId, utter(lessonId));
  }

  private int calls(AiCallKind kind) {
    return fakeAi.callCounts(USER).getOrDefault(kind, 0);
  }

  private int queryInt(String sql, Object... params) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        statement.setObject(i + 1, params[i]);
      }
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        return rows.getInt(1);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("テストの確認の SQL が失敗しました", e);
    }
  }

  private int unitUnderstanding() {
    return queryInt("select understanding from units where id = ?", unitId);
  }

  @Test
  void 開始すると_最初の発話を返し_ボードには模範解答と解説を含めない() {
    Started started = lessons.start(USER, materialId);

    assertEquals(20, started.maxRounds());
    assertEquals(START.plusSeconds(600), started.deadlineAt());
    assertEquals(PROBLEM, started.board().problemText());
    assertEquals("y = (x - 2)^2 と書いて手が止まっている", started.board().studentWork());
    assertEquals("なぜそうなるのですか？", started.opening().text());
    assertEquals(Expression.TROUBLED, started.opening().expression());
    assertEquals(1, DialogueFixtures.countTurns(dataSource, started.lessonId()));
    assertEquals(1, calls(AiCallKind.STUDENT_REPLY));
  }

  @Test
  void 最初の発話の_LLM_が失敗したら_固定の文言で始める() {
    fakeAi.script(
        USER, AiCallKind.STUDENT_REPLY, List.of(new ScriptedResponse(false, null)), false);

    Started started = lessons.start(USER, materialId);

    assertEquals(LessonService.FALLBACK_OPENING, started.opening().text());
    assertEquals(Expression.NORMAL, started.opening().expression());
  }

  @Test
  void 同じ利用者の進行中の授業は_新しい授業を始めると消える() {
    UUID first = startLesson();
    UUID second = startLesson();

    assertEquals(0, queryInt("select count(*) from lessons where id = ?", first));
    assertEquals(1, queryInt("select count(*) from lessons where id = ?", second));
    assertEquals(0, DialogueFixtures.countTurns(dataSource, first));
  }

  @Test
  void 今日のモードが通常でなければ_授業を始められない() {
    repositories.todayModes().save(USER, TodayMode.SELF_STUDY, LocalDate.now(clock));

    ConflictException e =
        assertThrows(ConflictException.class, () -> lessons.start(USER, materialId));

    assertEquals("mode_not_normal", e.code());
  }

  @Test
  void 整理済みでない教材では_授業を始められない() {
    UUID organizing = DialogueFixtures.insertMaterial(dataSource, unitId);

    ConflictException e =
        assertThrows(ConflictException.class, () -> lessons.start(USER, organizing));

    assertEquals("material_not_organized", e.code());
  }

  @Test
  void 今の単元の教材でなければ_授業を始められない() {
    settings.save(USER, command("二次関数の応用"));

    assertThrows(NotFoundException.class, () -> lessons.start(USER, materialId));
  }

  @Test
  void 発話_1_回ごとに_生徒の応答と学習評価を_1_回ずつ呼ぶ() {
    UUID lessonId = startLesson();
    int studentReplies = calls(AiCallKind.STUDENT_REPLY);
    int evaluations = calls(AiCallKind.EVALUATION);

    roundTrip(lessonId);

    assertEquals(1, calls(AiCallKind.STUDENT_REPLY) - studentReplies);
    assertEquals(1, calls(AiCallKind.EVALUATION) - evaluations);
  }

  @Test
  void 往復を_20_回くり返すと_それ以上は話せない() {
    UUID lessonId = startLesson();
    for (int round = 1; round < 20; round++) {
      Reply reply = roundTrip(lessonId);
      assertEquals(round, reply.roundCount());
      assertFalse(reply.limitReached());
    }

    Reply last = roundTrip(lessonId);

    assertEquals(20, last.roundCount());
    assertTrue(last.limitReached());
    ConflictException e =
        assertThrows(ConflictException.class, () -> lessons.utter(USER, lessonId, RECORDING));
    assertEquals("limit_reached", e.code());
  }

  @Test
  void 期限を過ぎた授業では_発話を受け付けない() {
    UUID lessonId = startLesson();
    clock.advance(Duration.ofSeconds(600));

    ConflictException e =
        assertThrows(ConflictException.class, () -> lessons.utter(USER, lessonId, RECORDING));

    assertEquals("limit_reached", e.code());
  }

  @Test
  void 生徒の応答が失敗したら_往復は増えず_話し直せる() {
    UUID lessonId = startLesson();
    UUID first = utter(lessonId);
    fakeAi.script(
        USER, AiCallKind.STUDENT_REPLY, List.of(new ScriptedResponse(false, null)), false);

    ExternalFailureException e =
        assertThrows(ExternalFailureException.class, () -> reply(lessonId, first));

    assertEquals("student_reply_failed", e.code());
    assertEquals(0, DialogueFixtures.lessonRoundCount(dataSource, lessonId));
    UUID second = utter(lessonId);
    assertEquals(1, DialogueFixtures.countUnrepliedUserTurns(dataSource, lessonId));
    assertEquals(1, reply(lessonId, second).roundCount());
  }

  @Test
  void 学習評価だけが失敗したら_度合いを変えずに応答する() {
    UUID lessonId = startLesson();
    fakeAi.script(USER, AiCallKind.EVALUATION, List.of(new ScriptedResponse(false, null)), false);

    Reply reply = roundTrip(lessonId);

    assertEquals(1, reply.roundCount());
    assertEquals(List.of(0, 0, 0), DialogueFixtures.lessonDegrees(dataSource, lessonId));
  }

  @Test
  void 度合いは_発話をまたいで_指導のポイントごとの最大値を保つ() {
    UUID lessonId = startLesson();
    fakeAi.script(
        USER, AiCallKind.EVALUATION, List.of(evaluation(1, 2, 0), evaluation(2, 0, 1)), false);

    roundTrip(lessonId);
    roundTrip(lessonId);

    assertEquals(List.of(2, 2, 1), DialogueFixtures.lessonDegrees(dataSource, lessonId));
  }

  @Test
  void 度合いが全部_2_なら_得点_6_で_理解度は_1_回で_30_上がる() {
    fakeAi.script(USER, AiCallKind.EVALUATION, List.of(evaluation(2, 2, 2)), false);
    UUID lessonId = startLesson();
    roundTrip(lessonId);
    clock.advance(NEAR_DEADLINE);

    Summary summary = lessons.summarize(USER, lessonId, PNG);

    assertEquals(lessonId, summary.recordId());
    assertEquals(1, summary.lessonsThisWeek());
    assertEquals(30, unitUnderstanding());
    assertEquals(1, repositories.units().lessonsSinceExam(unitId));
    assertEquals(1, queryInt("select count(*) from lesson_records where id = ?", lessonId));
    assertEquals(0, queryInt("select count(*) from lessons where id = ?", lessonId));
    assertEquals(0, DialogueFixtures.countTurns(dataSource, lessonId));
  }

  @Test
  void まとめの後の同じ授業の呼び出しは_LLM_を使わず_保存済みの結果を返す() {
    UUID lessonId = startLesson();
    roundTrip(lessonId);
    clock.advance(NEAR_DEADLINE);
    Summary first = lessons.summarize(USER, lessonId, PNG);

    Summary second = lessons.summarize(USER, lessonId, PNG);

    assertEquals(first.recordId(), second.recordId());
    assertEquals(first.summaryText(), second.summaryText());
    assertEquals(1, calls(AiCallKind.SUMMARY));
    assertEquals(1, queryInt("select count(*) from lesson_records"));
    // 既定の学習評価 (1、1、1) の得点 3 で、理解度は 15
    assertEquals(15, unitUnderstanding());
  }

  @Test
  void 上限に達していなければ_まとめを作らない() {
    UUID lessonId = startLesson();
    roundTrip(lessonId);

    ConflictException e =
        assertThrows(ConflictException.class, () -> lessons.summarize(USER, lessonId, PNG));

    assertEquals("limit_not_reached", e.code());
    assertEquals(0, calls(AiCallKind.SUMMARY));
  }

  @Test
  void まとめの作成が失敗しても_記録せず_授業は残り_やり直せる() {
    UUID lessonId = startLesson();
    roundTrip(lessonId);
    clock.advance(NEAR_DEADLINE);
    fakeAi.script(USER, AiCallKind.SUMMARY, List.of(new ScriptedResponse(false, null)), false);

    ExternalFailureException e =
        assertThrows(ExternalFailureException.class, () -> lessons.summarize(USER, lessonId, PNG));

    assertEquals("summary_failed", e.code());
    assertEquals(0, queryInt("select count(*) from lesson_records"));
    assertEquals(1, queryInt("select count(*) from lessons where id = ?", lessonId));
    assertEquals(lessonId, lessons.summarize(USER, lessonId, PNG).recordId());
  }

  @Test
  void 中断した授業は_記録せず_理解度も変えない() {
    UUID lessonId = startLesson();
    roundTrip(lessonId);
    lessons.abandon(USER, lessonId);

    assertThrows(NotFoundException.class, () -> lessons.summarize(USER, lessonId, PNG));
    assertEquals(0, queryInt("select count(*) from lesson_records"));
    assertEquals(0, unitUnderstanding());
    assertEquals(0, DialogueFixtures.countTurns(dataSource, lessonId));
  }

  @Test
  void 生徒の応答が失敗しても_学習評価の完了を待ってから返し_待っている間は_busy_になる() throws Exception {
    UUID lessonId = startLesson();
    UUID utteranceId = utter(lessonId);
    fakeAi.script(
        USER, AiCallKind.STUDENT_REPLY, List.of(new ScriptedResponse(false, null)), false);
    GatedAi gated = new GatedAi(fakeAi, AiCallKind.EVALUATION);
    LessonService gatedLessons = lessonsWith(gated);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<Reply> future =
          executor.submit(() -> gatedLessons.reply(USER, lessonId, utteranceId, PNG, List.of()));
      gated.awaitEntered();

      // 学習評価が終わるまで、応答は終わらない。その間は次の発話を受け付けない
      assertThrows(TimeoutException.class, () -> future.get(200, TimeUnit.MILLISECONDS));
      ConflictException busy =
          assertThrows(ConflictException.class, () -> lessons.utter(USER, lessonId, RECORDING));
      assertEquals("busy", busy.code());

      gated.release();
      ExecutionException e = assertThrows(ExecutionException.class, future::get);
      assertInstanceOf(ExternalFailureException.class, e.getCause());
      assertEquals("student_reply_failed", ((ExternalFailureException) e.getCause()).code());
      assertEquals(1, calls(AiCallKind.EVALUATION));
      assertEquals(0, DialogueFixtures.lessonRoundCount(dataSource, lessonId));
      // 応答が終わった後は、話し直せる
      assertTrue(lessons.utter(USER, lessonId, RECORDING).isPresent());
    } finally {
      gated.release();
      executor.shutdownNow();
    }
  }

  @Test
  void まとめの作成の最中に中断すると_記録せず_理解度も変えない() throws Exception {
    UUID lessonId = startLesson();
    roundTrip(lessonId);
    clock.advance(NEAR_DEADLINE);
    GatedAi gated = new GatedAi(fakeAi, AiCallKind.SUMMARY);
    LessonService gatedLessons = lessonsWith(gated);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<Summary> future = executor.submit(() -> gatedLessons.summarize(USER, lessonId, PNG));
      gated.awaitEntered();

      // まとめの LLM の呼び出しの間に中断し、その後で生成を完了させる
      lessons.abandon(USER, lessonId);
      gated.release();

      ExecutionException e = assertThrows(ExecutionException.class, future::get);
      assertInstanceOf(NotFoundException.class, e.getCause());
      assertEquals(0, queryInt("select count(*) from lesson_records"));
      assertEquals(0, queryInt("select count(*) from lessons where id = ?", lessonId));
      assertEquals(0, DialogueFixtures.countTurns(dataSource, lessonId));
      assertEquals(0, unitUnderstanding());
      assertEquals(0, repositories.units().lessonsSinceExam(unitId));
    } finally {
      gated.release();
      executor.shutdownNow();
    }
  }

  @Test
  void まとめが先に記録された後に中断しても_記録は残る() {
    UUID lessonId = startLesson();
    roundTrip(lessonId);
    clock.advance(NEAR_DEADLINE);
    lessons.summarize(USER, lessonId, PNG);

    lessons.abandon(USER, lessonId);

    assertEquals(1, queryInt("select count(*) from lesson_records where id = ?", lessonId));
    assertEquals(15, unitUnderstanding());
    assertEquals(1, repositories.units().lessonsSinceExam(unitId));
  }

  @Test
  void 授業の行が無ければ_確定は何も残さない() {
    Optional<RecordedLesson> recorded =
        repositories
            .lessonRecords()
            .complete(USER, UUID.randomUUID(), "まとめ", List.of(), clock.instant());

    assertTrue(recorded.isEmpty());
    assertEquals(0, queryInt("select count(*) from lesson_records"));
    assertEquals(0, unitUnderstanding());
  }

  @Test
  void 字幕の音声は_1_回だけ作る() {
    Started started = lessons.start(USER, materialId);

    byte[] first = lessons.audio(USER, started.lessonId(), started.opening().turnId());
    byte[] second = lessons.audio(USER, started.lessonId(), started.opening().turnId());

    assertArrayEquals(first, second);
    assertEquals(1, calls(AiCallKind.TTS));
  }

  @Test
  void 掃除は_開始から_60_分を過ぎた授業と相談を消す() {
    UUID old =
        DialogueFixtures.insertLesson(
            dataSource, USER, materialId, START.minus(Duration.ofMinutes(51)), 0);
    UUID recent =
        DialogueFixtures.insertLesson(dataSource, USER, materialId, START.plusSeconds(600), 0);
    UUID oldConsultation =
        DialogueFixtures.insertConsultation(
            dataSource, USER, materialId, START.minus(Duration.ofMinutes(51)), 0);

    new DialogueSweeper(repositories.dialogues(), clock).sweep();

    assertEquals(0, queryInt("select count(*) from lessons where id = ?", old));
    assertEquals(1, queryInt("select count(*) from lessons where id = ?", recent));
    assertEquals(0, queryInt("select count(*) from consultations where id = ?", oldConsultation));
  }

  /**
   * 指定した種類の呼び出しを、解放されるまで待たせる LLM。待っている間の他の処理を確かめるために使う。
   *
   * <p>待たせる前に、呼び出しに入ったことを {@link #awaitEntered} で待てる。
   */
  private static final class GatedAi implements LanguageModel {

    private final LanguageModel delegate;
    private final AiCallKind gated;
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);

    GatedAi(LanguageModel delegate, AiCallKind gated) {
      this.delegate = delegate;
      this.gated = gated;
    }

    void awaitEntered() throws InterruptedException {
      assertTrue(entered.await(5, TimeUnit.SECONDS));
    }

    void release() {
      released.countDown();
    }

    private void gate(AiCallKind kind) {
      if (kind != gated) {
        return;
      }
      entered.countDown();
      try {
        released.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }

    @Override
    public OrganizeResult organize(AiCallContext context, List<Media> images) {
      return delegate.organize(context, images);
    }

    @Override
    public StudentReply studentReply(AiCallContext context, StudentReplyInput input) {
      gate(context.kind());
      return delegate.studentReply(context, input);
    }

    @Override
    public Evaluation evaluate(AiCallContext context, EvaluationInput input) {
      gate(context.kind());
      return delegate.evaluate(context, input);
    }

    @Override
    public MentorReply mentorReply(AiCallContext context, MentorReplyInput input) {
      return delegate.mentorReply(context, input);
    }

    @Override
    public SummaryResult summarize(AiCallContext context, SummaryInput input) {
      gate(context.kind());
      return delegate.summarize(context, input);
    }

    @Override
    public MockExamResult mockExam(AiCallContext context, MockExamInput input) {
      return delegate.mockExam(context, input);
    }

    @Override
    public HomeworkJudgement gradeHomework(AiCallContext context, Media photo) {
      return delegate.gradeHomework(context, photo);
    }
  }
}
