package com.teachme.application;

import com.teachme.ai.AiCallContext;
import com.teachme.ai.AiCallKind;
import com.teachme.ai.LanguageModel;
import com.teachme.ai.LanguageModel.LastLesson;
import com.teachme.ai.LanguageModel.MockExamInput;
import com.teachme.domain.Deviation;
import com.teachme.domain.MockExamOpportunity;
import com.teachme.domain.UserSettings;
import com.teachme.persistence.MaterialRepository;
import com.teachme.persistence.MaterialRepository.MaterialRow;
import com.teachme.persistence.MockExamRepository;
import com.teachme.persistence.MockExamRepository.MockExamRow;
import com.teachme.persistence.MockExamRepository.NewMockExam;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模試 (仕様書 5.7、アーキテクチャ設計 4.1、7.3)。模試の機会があるときに挑戦し、外部 AI で類題、解答用紙、生徒のコメントを作る。
 *
 * <p>成功したときだけ結果を保存し、模試の機会を消す。失敗したときは何も保存せず、機会は残す。
 */
public final class MockExamService {

  static final String NO_OPPORTUNITY_CODE = "no_opportunity";
  static final String NO_OPPORTUNITY_MESSAGE = "模試にはまだ挑戦できません";
  static final String BUSY_CODE = "busy";
  static final String BUSY_MESSAGE = "処理中です。少し待ってから、もう一度お試しください";
  static final String MOCK_EXAM_FAILED_CODE = "mock_exam_failed";
  static final String MOCK_EXAM_FAILED_MESSAGE = "模試を作れませんでした";

  private static final Logger LOG = LoggerFactory.getLogger(MockExamService.class);

  private final SettingsService settings;
  private final MockExamRepository mockExams;
  private final MaterialRepository materials;
  private final LanguageModel llm;
  private final Clock clock;

  public MockExamService(
      SettingsService settings,
      MockExamRepository mockExams,
      MaterialRepository materials,
      LanguageModel llm,
      Clock clock) {
    this.settings = settings;
    this.mockExams = mockExams;
    this.materials = materials;
    this.llm = llm;
    this.clock = clock;
  }

  /**
   * 模試に挑戦する。結果を保存し、保存後の結果と偏差値の推移を返す。
   *
   * @throws ConflictException 設定が未完了 (`setup_required`)、模試の機会が無い (`no_opportunity`)、同じ単元の挑戦が進行中
   *     (`busy`)
   * @throws ExternalFailureException 外部 AI の失敗 (`mock_exam_failed`)。何も保存せず、機会は残る
   */
  public MockExamView take(UUID userId) {
    UserSettings current = settings.requireCompleted(userId);
    UUID unitId = current.unitId();
    // 直近の授業の教材は、挑戦の前に読む。教材が消えていれば、無しとして作る
    Optional<LastLesson> lastLesson = lastLesson(userId, unitId);
    Optional<NewMockExam> saved =
        mockExams.challenge(
            unitId,
            unit -> {
              if (!MockExamOpportunity.isAvailable(unit.lessonsSinceExam())) {
                throw new ConflictException(NO_OPPORTUNITY_CODE, NO_OPPORTUNITY_MESSAGE);
              }
              LanguageModel.MockExamResult made =
                  createWithLlm(
                      userId,
                      new MockExamInput(
                          current.studentType(),
                          current.subject(),
                          current.unitName(),
                          unit.understanding(),
                          lastLesson.orElse(null)));
              return new NewMockExam(
                  UUID.randomUUID(),
                  clock.instant(),
                  unit.understanding(),
                  Deviation.of(current.studentType(), unit.understanding()),
                  made.problemText(),
                  made.answerSheet(),
                  made.studentComment());
            });
    NewMockExam exam = saved.orElseThrow(() -> new ConflictException(BUSY_CODE, BUSY_MESSAGE));
    return new MockExamView(
        exam.id(),
        exam.takenAt(),
        current.unitName(),
        exam.score(),
        exam.deviation(),
        exam.problemText(),
        exam.answerSheet(),
        exam.studentComment(),
        history(unitId));
  }

  /**
   * 保存済みの模試の結果と、同じ単元の偏差値の推移。
   *
   * @throws ConflictException 設定が未完了のとき (`setup_required`)
   * @throws NotFoundException 無い模試、またはほかの利用者の模試のとき
   */
  public MockExamView get(UUID userId, UUID mockExamId) {
    settings.requireCompleted(userId);
    MockExamRow row = mockExams.find(userId, mockExamId).orElseThrow(NotFoundException::new);
    return new MockExamView(
        row.id(),
        row.takenAt(),
        row.unitName(),
        row.score(),
        row.deviation(),
        row.problemText(),
        row.answerSheet(),
        row.studentComment(),
        history(row.unitId()));
  }

  /** 単元の偏差値の推移 (古い順)。 */
  private List<DeviationPoint> history(UUID unitId) {
    return mockExams.history(unitId).stream()
        .map(point -> new DeviationPoint(point.takenAt(), point.deviation()))
        .toList();
  }

  /** 直近の授業が使った教材の問題とポイント。授業の記録が無いか、教材が消えていれば空。 */
  private Optional<LastLesson> lastLesson(UUID userId, UUID unitId) {
    return mockExams
        .latestLessonMaterialRef(unitId)
        .flatMap(materialId -> materials.find(userId, materialId))
        .map(MaterialRow::problem)
        .map(problem -> new LastLesson(problem.problemText(), problem.teachingPoints()));
  }

  private LanguageModel.MockExamResult createWithLlm(UUID userId, MockExamInput input) {
    LanguageModel.MockExamResult made;
    try {
      made = llm.mockExam(new AiCallContext(userId, AiCallKind.MOCK_EXAM), input);
    } catch (RuntimeException e) {
      LOG.warn("模試の作成に失敗しました: {}", e.getClass().getName());
      throw mockExamFailed();
    }
    if (made == null
        || isBlank(made.problemText())
        || isBlank(made.answerSheet())
        || isBlank(made.studentComment())) {
      throw mockExamFailed();
    }
    return made;
  }

  private static ExternalFailureException mockExamFailed() {
    return new ExternalFailureException(MOCK_EXAM_FAILED_CODE, MOCK_EXAM_FAILED_MESSAGE);
  }

  private static boolean isBlank(String text) {
    return text == null || text.isBlank();
  }

  /**
   * 模試の結果と、同じ単元の偏差値の推移。
   *
   * @param unitName 単元の名前
   * @param history 偏差値の推移 (古い順)。この結果を含む
   */
  public record MockExamView(
      UUID id,
      Instant takenAt,
      String unitName,
      int score,
      BigDecimal deviation,
      String problemText,
      String answerSheet,
      String studentComment,
      List<DeviationPoint> history) {}

  /** 偏差値の推移の 1 点。 */
  public record DeviationPoint(Instant takenAt, BigDecimal deviation) {}
}
