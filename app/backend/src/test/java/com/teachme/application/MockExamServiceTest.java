package com.teachme.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.teachme.application.MockExamService.MockExamView;
import com.teachme.application.SettingsService.SettingsCommand;
import com.teachme.domain.OrganizedProblem;
import com.teachme.domain.StudentType;
import com.teachme.domain.Subject;
import com.teachme.domain.UnitName;
import com.teachme.persistence.MaterialRepository.NewImage;
import com.teachme.persistence.Repositories;
import com.teachme.testsupport.Fixtures;
import com.teachme.testsupport.MutableClock;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 模試の統合テスト。DB は Testcontainers の PostgreSQL を使い、外部 AI は {@link FakeAi} で置き換える。 */
class MockExamServiceTest {
  private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final Instant START = Instant.parse("2026-10-21T01:00:00Z");
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};
  private static final String PROBLEM = "y = x^2 - 4x + 3 の頂点を求めなさい";
  private static final List<String> POINTS = List.of("平方完成をする", "頂点を読む", "符号に注意する");

  private DataSource dataSource;
  private Repositories repositories;
  private MutableClock clock;
  private SettingsService settings;
  private RecordingAi ai;
  private MockExamService mockExams;
  private UUID unitId;
  private UUID materialId;
  private ExecutorService executor;

  @BeforeEach
  void setUp() {
    dataSource = Fixtures.freshDataSource();
    repositories = new Repositories(dataSource);
    clock = new MutableClock(START, Fixtures.JAPAN);
    settings = new SettingsService(repositories.settings(), clock);
    ai = new RecordingAi(new FakeAi());
    settings.save(USER, command("二次関数"));
    settings.save(OTHER, command("三角比"));
    mockExams = servicesWith(ai);
    unitId = settings.find(USER).orElseThrow().unitId();
    materialId = organizedMaterial(unitId);
    executor = Executors.newSingleThreadExecutor();
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  private MockExamService servicesWith(LanguageModel llm) {
    return new MockExamService(
        settings, repositories.mockExams(), repositories.materials(), llm, clock);
  }

  private static SettingsCommand command(String unitName) {
    return new SettingsCommand(
        StudentType.LOGICAL, Subject.MATH, UnitName.parse(unitName).orElseThrow(), 3, true);
  }

  /** 整理済みの教材を 1 件入れる。問題文と指導のポイント 3 件を持つ。 */
  private UUID organizedMaterial(UUID unit) {
    UUID id = UUID.randomUUID();
    repositories
        .materials()
        .insertOrganizing(id, unit, List.of(new NewImage("image/png", PNG)), clock.instant());
    repositories
        .materials()
        .markOrganized(
            id,
            OrganizedProblem.parse(
                    PROBLEM,
                    List.of(),
                    "頂点は (2, -1)",
                    List.of("平方完成の形を作る"),
                    "y = (x - 2)^2 と書いて手が止まっている",
                    POINTS,
                    1)
                .orElseThrow());
    return id;
  }

  /** 単元の理解度と、前回の模試からの授業の完了数を決める。 */
  private void setUnit(int understanding, int lessonsSinceExam) {
    execute(
        "update units set understanding = ?, lessons_since_exam = ? where id = ?",
        (short) understanding,
        (short) lessonsSinceExam,
        unitId);
  }

  /** 直近の授業の記録を 1 件入れる。授業が使った教材を指す。 */
  private void insertLessonRecord(UUID material) {
    execute(
        "insert into lesson_records (id, user_id, unit_id, material_ref, completed_at, degrees,"
            + " summary_text) values (?, ?, ?, ?, ?, '{1,1,1}', 'まとめ')",
        UUID.randomUUID(),
        USER,
        unitId,
        material,
        OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
  }

  private void execute(String sql, Object... params) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        statement.setObject(i + 1, params[i]);
      }
      statement.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException("テストの準備の SQL が失敗しました", e);
    }
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

  private int lessonsSinceExam() {
    return queryInt("select lessons_since_exam from units where id = ?", unitId);
  }

  private int savedExamCount() {
    return queryInt("select count(*) from mock_exams where unit_id = ?", unitId);
  }

  @Test
  void 機会があるときに挑戦すると_得点と偏差値を保存し_機会を消す() {
    setUnit(90, 3);

    MockExamView view = mockExams.take(USER);

    assertEquals(90, view.score());
    assertEquals("69.0", view.deviation().toPlainString());
    assertEquals("二次関数", view.unitName());
    assertEquals(0, lessonsSinceExam());
    assertEquals(1, savedExamCount());
    assertEquals(1, view.history().size());
    assertEquals(view.deviation(), view.history().get(0).deviation());
  }

  @Test
  void 機会が無いときは拒否し_外部_AI_を呼ばない() {
    setUnit(90, 2);

    ConflictException e = assertThrows(ConflictException.class, () -> mockExams.take(USER));

    assertEquals("no_opportunity", e.code());
    assertEquals(0, ai.callCount(AiCallKind.MOCK_EXAM));
    assertEquals(0, savedExamCount());
  }

  @Test
  void 外部_AI_が失敗したら_結果を保存せず_機会も残り_やり直せる() {
    setUnit(40, 3);
    ai.script(AiCallKind.MOCK_EXAM, List.of(new ScriptedResponse(false, null)));

    ExternalFailureException e =
        assertThrows(ExternalFailureException.class, () -> mockExams.take(USER));

    assertEquals("mock_exam_failed", e.code());
    assertEquals(3, lessonsSinceExam());
    assertEquals(0, savedExamCount());

    MockExamView retried = mockExams.take(USER);

    assertEquals(40, retried.score());
    assertEquals(0, lessonsSinceExam());
    assertEquals(1, savedExamCount());
  }

  @Test
  void 同時の挑戦は_後の側が_busy_になり_外部_AI_は_1_回しか呼ばれない() throws Exception {
    setUnit(60, 3);
    ai.gate();

    Future<MockExamView> first = executor.submit(() -> mockExams.take(USER));
    ai.awaitEntered();

    ConflictException e = assertThrows(ConflictException.class, () -> mockExams.take(USER));

    assertEquals("busy", e.code());
    ai.release();
    assertEquals(60, first.get(10, TimeUnit.SECONDS).score());
    assertEquals(1, ai.callCount(AiCallKind.MOCK_EXAM));
    assertEquals(1, savedExamCount());
    assertEquals(0, lessonsSinceExam());
  }

  @Test
  void 偏差値の推移は_古い順に返る() {
    setUnit(10, 3);
    mockExams.take(USER);
    clock.advance(Duration.ofDays(7));
    setUnit(50, 3);
    mockExams.take(USER);
    clock.advance(Duration.ofDays(7));
    setUnit(90, 3);

    MockExamView last = mockExams.take(USER);

    assertEquals(
        List.of("61.0", "65.0", "69.0"),
        last.history().stream().map(point -> point.deviation().toPlainString()).toList());
    assertTrue(last.history().get(0).takenAt().isBefore(last.history().get(2).takenAt()));
  }

  @Test
  void 直近の授業の教材が残っていれば_問題とポイントを渡す() {
    setUnit(50, 3);
    insertLessonRecord(materialId);

    mockExams.take(USER);

    MockExamInput input = ai.lastMockExamInput();
    assertEquals(PROBLEM, input.lastLesson().problemText());
    assertEquals(POINTS, input.lastLesson().teachingPoints());
    assertEquals("二次関数", input.unitName());
    assertEquals(50, input.score());
  }

  @Test
  void 直近の授業の教材が消えていれば_問題とポイントを渡さずに作る() {
    setUnit(50, 3);
    insertLessonRecord(materialId);
    execute("delete from materials where id = ?", materialId);

    MockExamView view = mockExams.take(USER);

    assertNull(ai.lastMockExamInput().lastLesson());
    assertEquals(1, savedExamCount());
    assertEquals("65.0", view.deviation().toPlainString());
  }

  @Test
  void 結果は_利用者の模試としてだけ返り_ほかの利用者からは見えない() {
    setUnit(90, 3);
    MockExamView view = mockExams.take(USER);

    MockExamView fetched = mockExams.get(USER, view.id());

    assertEquals(view.id(), fetched.id());
    assertEquals(view.score(), fetched.score());
    assertEquals(view.history(), fetched.history());
    assertThrows(NotFoundException.class, () -> mockExams.get(OTHER, view.id()));
  }

  @Test
  void 設定が未完了の利用者は_setup_required_になる() {
    UUID unknown = UUID.fromString("33333333-3333-3333-3333-333333333333");

    ConflictException e = assertThrows(ConflictException.class, () -> mockExams.take(unknown));

    assertEquals("setup_required", e.code());
    assertEquals(0, ai.callCount(AiCallKind.MOCK_EXAM));
  }

  /**
   * 模試の呼び出しを記録し、待たせられる LLM。模試以外は {@link FakeAi} に任せる。
   *
   * <p>{@link #gate} の後の模試の呼び出しは、呼び出しに入ったところで止まる。{@link #awaitEntered} で止まったことを待ち、{@link #release}
   * で解放する。
   */
  private static final class RecordingAi implements LanguageModel {

    private final FakeAi delegate;
    private final List<MockExamInput> mockExamInputs = new CopyOnWriteArrayList<>();
    private final Map<AiCallKind, Integer> counts = new ConcurrentHashMap<>();
    private volatile Gate gate;

    RecordingAi(FakeAi delegate) {
      this.delegate = delegate;
    }

    void script(AiCallKind kind, List<ScriptedResponse> responses) {
      delegate.script(USER, kind, responses, false);
    }

    /** 次の模試の呼び出しを、解放されるまで止める。 */
    void gate() {
      gate = new Gate();
    }

    void awaitEntered() throws InterruptedException {
      assertTrue(gate.entered.await(5, TimeUnit.SECONDS));
    }

    void release() {
      gate.released.countDown();
    }

    int callCount(AiCallKind kind) {
      return counts.getOrDefault(kind, 0);
    }

    MockExamInput lastMockExamInput() {
      return mockExamInputs.get(mockExamInputs.size() - 1);
    }

    @Override
    public MockExamResult mockExam(AiCallContext context, MockExamInput input) {
      counts.merge(AiCallKind.MOCK_EXAM, 1, Integer::sum);
      mockExamInputs.add(input);
      Gate current = gate;
      if (current != null) {
        current.enter();
      }
      return delegate.mockExam(context, input);
    }

    @Override
    public OrganizeResult organize(AiCallContext context, List<Media> images) {
      return delegate.organize(context, images);
    }

    @Override
    public StudentReply studentReply(AiCallContext context, StudentReplyInput input) {
      return delegate.studentReply(context, input);
    }

    @Override
    public Evaluation evaluate(AiCallContext context, EvaluationInput input) {
      return delegate.evaluate(context, input);
    }

    @Override
    public MentorReply mentorReply(AiCallContext context, MentorReplyInput input) {
      return delegate.mentorReply(context, input);
    }

    @Override
    public SummaryResult summarize(AiCallContext context, SummaryInput input) {
      return delegate.summarize(context, input);
    }

    @Override
    public HomeworkJudgement gradeHomework(AiCallContext context, Media photo) {
      return delegate.gradeHomework(context, photo);
    }

    /** 呼び出しに入ったことを知らせ、解放されるまで待たせる。 */
    private static final class Gate {
      private final CountDownLatch entered = new CountDownLatch(1);
      private final CountDownLatch released = new CountDownLatch(1);

      void enter() {
        entered.countDown();
        try {
          assertTrue(released.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e);
        }
      }
    }
  }
}
