package com.teachme.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.teachme.application.ConsultationService.Reply;
import com.teachme.application.ConsultationService.Started;
import com.teachme.application.DialogueService.Recording;
import com.teachme.application.SettingsService.SettingsCommand;
import com.teachme.domain.BoardWrite;
import com.teachme.domain.DialogueLimits;
import com.teachme.domain.OrganizedProblem;
import com.teachme.domain.StudentType;
import com.teachme.domain.Subject;
import com.teachme.domain.UnitName;
import com.teachme.persistence.MaterialRepository.NewImage;
import com.teachme.persistence.Repositories;
import com.teachme.testsupport.DialogueFixtures;
import com.teachme.testsupport.Fixtures;
import com.teachme.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 先輩先生への相談の統合テスト。DB は Testcontainers の PostgreSQL を使い、外部 AI は {@link FakeAi} で置き換える。 */
class ConsultationServiceTest {
  private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final Instant START = Instant.parse("2026-10-21T01:00:00Z");
  private static final Recording RECORDING = new Recording("audio/webm", new byte[] {1, 2, 3});
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};
  private static final String PROBLEM = "y = x^2 - 4x + 3 の頂点を求めなさい";
  private static final List<String> POINTS = List.of("平方完成をする", "頂点を読む", "符号に注意する");
  private static final List<String> STEPS = List.of("平方完成の形を作る");

  private DataSource dataSource;
  private Repositories repositories;
  private MutableClock clock;
  private SettingsService settings;
  private FakeAi fakeAi;
  private ConsultationService consultations;
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
    consultations = consultationsWith(fakeAi);
    UUID unitId = settings.find(USER).orElseThrow().unitId();
    materialId = organizedMaterial(unitId);
  }

  /** 外部 AI を差し替えた相談のサービス。他は、setUp の相談のサービスと同じ部品を使う。 */
  private ConsultationService consultationsWith(LanguageModel llm) {
    DialogueService dialogue =
        new DialogueService(settings, repositories.dialogues(), fakeAi, fakeAi, clock);
    return new ConsultationService(
        settings,
        dialogue,
        repositories.dialogues(),
        repositories.materials(),
        llm,
        id -> DialogueLimits.consultation(),
        clock);
  }

  private static SettingsCommand command(String unitName) {
    return new SettingsCommand(
        StudentType.LOGICAL, Subject.MATH, UnitName.parse(unitName).orElseThrow(), 3, true);
  }

  /** 整理済みの教材を 1 件入れる。 */
  private UUID organizedMaterial(UUID unitId) {
    UUID id = UUID.randomUUID();
    repositories
        .materials()
        .insertOrganizing(id, unitId, List.of(new NewImage("image/png", PNG)), clock.instant());
    repositories.materials().markOrganized(id, problem());
    return id;
  }

  private static OrganizedProblem problem() {
    return OrganizedProblem.parse(
            PROBLEM, List.of(), "頂点は (2, -1)", STEPS, "y = (x - 2)^2 と書いて手が止まっている", POINTS, 1)
        .orElseThrow();
  }

  /** 先輩先生の応答の台本。ボードへの書き込みは、与えたものをそのまま返す。 */
  private static ScriptedResponse mentorReply(String text, List<Map<String, Object>> writes) {
    return new ScriptedResponse(true, Map.<String, Object>of("text", text, "boardWrites", writes));
  }

  private UUID startConsultation() {
    return consultations.start(USER, materialId).consultationId();
  }

  /** 発話を 1 件受け付け、その字幕の識別子を返す。 */
  private UUID utter(UUID consultationId) {
    return consultations.utter(USER, consultationId, RECORDING).orElseThrow().utteranceId();
  }

  private Reply reply(UUID consultationId, UUID utteranceId) {
    return consultations.reply(USER, consultationId, utteranceId, PNG, List.of());
  }

  /** 発話 1 回と、その応答 1 回。 */
  private Reply roundTrip(UUID consultationId) {
    return reply(consultationId, utter(consultationId));
  }

  private int calls(AiCallKind kind) {
    return fakeAi.callCounts(USER).getOrDefault(kind, 0);
  }

  @Test
  void 開始すると_問題文と図表を返し_模範解答は含めない() {
    Started started = consultations.start(USER, materialId);

    assertEquals(15, started.maxRounds());
    assertEquals(START.plusSeconds(600), started.deadlineAt());
    assertEquals(PROBLEM, started.problemText());
    assertEquals(List.of(), started.figures());
    // 相談は、先輩先生が最初に話さない。字幕は利用者の発話から始まる
    assertEquals(0, DialogueFixtures.countTurns(dataSource, started.consultationId()));
  }

  @Test
  void 整理済みでない教材では_相談を始められない() {
    UUID organizing = UUID.randomUUID();
    repositories
        .materials()
        .insertOrganizing(
            organizing,
            settings.find(USER).orElseThrow().unitId(),
            List.of(new NewImage("image/png", PNG)),
            clock.instant());

    ConflictException e =
        assertThrows(ConflictException.class, () -> consultations.start(USER, organizing));

    assertEquals("material_not_organized", e.code());
  }

  @Test
  void 今の単元の教材でなければ_相談を始められない() {
    UUID otherUnitMaterial = organizedMaterial(settings.find(OTHER).orElseThrow().unitId());

    assertThrows(NotFoundException.class, () -> consultations.start(USER, otherUnitMaterial));
  }

  @Test
  void 開始し直すと_進行中の相談は中断として消える() {
    UUID first = startConsultation();

    UUID second = startConsultation();

    assertThrows(NotFoundException.class, () -> utter(first));
    assertEquals(0, DialogueFixtures.countTurns(dataSource, first));
    assertTrue(consultations.utter(USER, second, RECORDING).isPresent());
  }

  @Test
  void 発話_1_回ごとに_先輩先生の応答を_1_回だけ呼び_生徒の応答と学習評価は呼ばない() {
    UUID consultationId = startConsultation();
    int mentorReplies = calls(AiCallKind.MENTOR_REPLY);

    roundTrip(consultationId);

    assertEquals(1, calls(AiCallKind.MENTOR_REPLY) - mentorReplies);
    assertEquals(0, calls(AiCallKind.STUDENT_REPLY));
    assertEquals(0, calls(AiCallKind.EVALUATION));
  }

  @Test
  void 応答すると_先輩先生の字幕を入れ_往復を_1_増やす() {
    UUID consultationId = startConsultation();

    Reply reply = roundTrip(consultationId);

    assertEquals(1, reply.roundCount());
    assertFalse(reply.limitReached());
    assertEquals("ここは平方完成を使うと考えやすいですよ。", reply.turn().text());
    assertEquals(2, DialogueFixtures.countTurns(dataSource, consultationId));
  }

  @Test
  void 往復を_15_回くり返すと_それ以上は話せない() {
    UUID consultationId = startConsultation();
    for (int round = 1; round < 15; round++) {
      Reply reply = roundTrip(consultationId);
      assertEquals(round, reply.roundCount());
      assertFalse(reply.limitReached());
    }

    Reply last = roundTrip(consultationId);

    assertEquals(15, last.roundCount());
    assertTrue(last.limitReached());
    ConflictException e =
        assertThrows(
            ConflictException.class, () -> consultations.utter(USER, consultationId, RECORDING));
    assertEquals("limit_reached", e.code());
  }

  @Test
  void 期限を過ぎた相談では_発話を受け付けない() {
    UUID consultationId = startConsultation();
    clock.advance(Duration.ofSeconds(600));

    ConflictException e =
        assertThrows(
            ConflictException.class, () -> consultations.utter(USER, consultationId, RECORDING));

    assertEquals("limit_reached", e.code());
  }

  @Test
  void 先輩先生の応答が失敗したら_往復は増えず_話し直せる() {
    UUID consultationId = startConsultation();
    UUID first = utter(consultationId);
    fakeAi.script(USER, AiCallKind.MENTOR_REPLY, List.of(new ScriptedResponse(false, null)), false);

    ExternalFailureException e =
        assertThrows(ExternalFailureException.class, () -> reply(consultationId, first));

    assertEquals("mentor_reply_failed", e.code());
    assertEquals(0, roundCountOf(consultationId));
    assertEquals(1, calls(AiCallKind.MENTOR_REPLY));
    assertEquals(1, DialogueFixtures.countUnrepliedUserTurns(dataSource, consultationId));

    // 失敗の後は、話し直せる。新しい発話が、未応答の発話を置き換える
    UUID second = utter(consultationId);
    assertEquals(1, DialogueFixtures.countUnrepliedUserTurns(dataSource, consultationId));
    Reply retried = reply(consultationId, second);
    assertEquals(1, retried.roundCount());
  }

  @Test
  void 先輩先生の応答が空の文章なら_失敗として_往復は増えない() {
    UUID consultationId = startConsultation();
    UUID utterance = utter(consultationId);
    fakeAi.script(USER, AiCallKind.MENTOR_REPLY, List.of(mentorReply(" ", List.of())), false);

    ExternalFailureException e =
        assertThrows(ExternalFailureException.class, () -> reply(consultationId, utterance));

    assertEquals("mentor_reply_failed", e.code());
    assertEquals(0, roundCountOf(consultationId));
  }

  @Test
  void 形の外れたボードへの書き込みは_無いものとして_残りを返す() {
    UUID consultationId = startConsultation();
    UUID utterance = utter(consultationId);
    fakeAi.script(
        USER,
        AiCallKind.MENTOR_REPLY,
        List.of(
            mentorReply(
                "判別式を使います",
                List.of(
                    Map.of("kind", "text", "text", "D = b^2 - 4ac"),
                    Map.of("kind", "text", "text", " "),
                    Map.of("kind", "highlight"),
                    Map.of(
                        "kind",
                        "highlight",
                        "region",
                        Map.of("x", 0.9, "y", 0.9, "width", 0.5, "height", 0.1)),
                    Map.of(
                        "kind",
                        "highlight",
                        "region",
                        Map.of("x", 0.1, "y", 0.2, "width", 0.3, "height", 0.1))))),
        false);

    Reply reply = reply(consultationId, utterance);

    assertEquals(
        List.of(
            new BoardWrite(BoardWrite.Kind.TEXT, "D = b^2 - 4ac", null),
            new BoardWrite(
                BoardWrite.Kind.HIGHLIGHT, null, new OrganizedProblem.Region(0.1, 0.2, 0.3, 0.1))),
        reply.boardWrites());
  }

  @Test
  void 中断した後の発話と応答は_結果を捨て_字幕を残さない() {
    UUID consultationId = startConsultation();
    UUID utterance = utter(consultationId);

    consultations.abandon(USER, consultationId);

    assertThrows(NotFoundException.class, () -> utter(consultationId));
    assertThrows(NotFoundException.class, () -> reply(consultationId, utterance));
    assertEquals(0, DialogueFixtures.countTurns(dataSource, consultationId));
  }

  @Test
  void 先輩先生の応答の最中に中断すると_応答を捨て_字幕を残さない() {
    UUID consultationId = startConsultation();
    UUID utterance = utter(consultationId);
    ConsultationService interrupted =
        consultationsWith(
            new AbandonDuringReply(fakeAi, () -> consultations.abandon(USER, consultationId)));

    assertThrows(
        NotFoundException.class,
        () -> interrupted.reply(USER, consultationId, utterance, PNG, List.of()));

    assertEquals(0, DialogueFixtures.countTurns(dataSource, consultationId));
    assertThrows(NotFoundException.class, () -> utter(consultationId));
  }

  @Test
  void 同じ利用者の相談のほかは_発話できない() {
    UUID consultationId = startConsultation();

    assertThrows(
        NotFoundException.class, () -> consultations.utter(OTHER, consultationId, RECORDING));
  }

  private int roundCountOf(UUID consultationId) {
    return DialogueFixtures.consultationRoundCount(dataSource, consultationId);
  }

  /** 先輩先生の応答の直前に、指定の処理を 1 回行う外部 AI。中断が応答の途中で入る場合を確かめるために使う。 */
  private static final class AbandonDuringReply implements LanguageModel {

    private final LanguageModel delegate;
    private final Runnable beforeMentorReply;

    AbandonDuringReply(LanguageModel delegate, Runnable beforeMentorReply) {
      this.delegate = delegate;
      this.beforeMentorReply = beforeMentorReply;
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
      beforeMentorReply.run();
      return delegate.mentorReply(context, input);
    }

    @Override
    public SummaryResult summarize(AiCallContext context, SummaryInput input) {
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
