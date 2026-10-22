package com.teachme.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.teachme.application.RecordService.LearningReport;
import com.teachme.application.RecordService.RecordUnitView;
import com.teachme.application.RecordService.SummaryNoteView;
import com.teachme.application.RecordService.UnitSummary;
import com.teachme.application.SettingsService.SettingsCommand;
import com.teachme.domain.StudentType;
import com.teachme.domain.Subject;
import com.teachme.domain.UnitName;
import com.teachme.persistence.Repositories;
import com.teachme.testsupport.Fixtures;
import com.teachme.testsupport.MutableClock;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 学習記録の統合テスト。DB は Testcontainers の PostgreSQL を使い、集計、削除の冪等、利用者の隔離を確かめる。 */
class RecordServiceTest {
  private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID UNKNOWN = UUID.fromString("33333333-3333-3333-3333-333333333333");

  private static final Instant FIRST = Instant.parse("2026-10-01T01:00:00Z");
  private static final Instant SECOND = Instant.parse("2026-10-08T01:00:00Z");

  private DataSource dataSource;
  private Repositories repositories;
  private SettingsService settings;
  private RecordService records;
  private UUID unitId;

  @BeforeEach
  void setUp() {
    dataSource = Fixtures.freshDataSource();
    repositories = new Repositories(dataSource);
    MutableClock clock = new MutableClock(Instant.parse("2026-10-21T01:00:00Z"), Fixtures.JAPAN);
    settings = new SettingsService(repositories.settings(), clock);
    records = new RecordService(settings, repositories.records());
    save(USER, "二次関数");
    unitId = settings.find(USER).orElseThrow().unitId();
    save(OTHER, "三角比");
  }

  @Test
  void 記録の無い単元は一覧に出さず_記録のある単元だけを作成順に返す() throws SQLException {
    UUID second = saveAndFindUnit(USER, "図形");
    insertLessonRecord(USER, UUID.randomUUID(), unitId, UUID.randomUUID(), FIRST, "[]");

    assertEquals(List.of(summary(unitId, "二次関数", 1, 0)), records.listUnits(USER));

    insertMockExam(second, FIRST, 50, "50.0");
    assertEquals(
        List.of(summary(unitId, "二次関数", 1, 0), summary(second, "図形", 0, 1)),
        records.listUnits(USER));
  }

  @Test
  void 授業の回数は_教材を消しても_教えた問題の数は残る() throws SQLException {
    UUID materialOne = insertMaterial(unitId);
    UUID materialTwo = insertMaterial(unitId);
    insertLessonRecord(USER, UUID.randomUUID(), unitId, materialOne, FIRST, "[]");
    insertLessonRecord(USER, UUID.randomUUID(), unitId, materialOne, SECOND, "[]");
    insertLessonRecord(USER, UUID.randomUUID(), unitId, materialTwo, SECOND, "[]");

    assertEquals(3, records.getUnit(USER, unitId).report().lessonCount());
    assertEquals(2, records.getUnit(USER, unitId).report().taughtProblemCount());

    repositories.materials().delete(materialOne);

    LearningReport report = records.getUnit(USER, unitId).report();
    assertEquals(3, report.lessonCount());
    assertEquals(2, report.taughtProblemCount());
  }

  @Test
  void 最新の模試と偏差値の推移は_新しい順に返す() throws SQLException {
    insertMockExam(unitId, FIRST, 40, "49.0");
    insertMockExam(unitId, SECOND, 70, "67.0");

    RecordUnitView view = records.getUnit(USER, unitId);

    assertEquals(
        List.of(SECOND, FIRST),
        view.mockExams().stream().map(RecordService.MockExamEntry::takenAt).toList());
    assertEquals(70, view.report().latestMockExam().score());
    assertEquals(new BigDecimal("67.0"), view.report().latestMockExam().deviation());
  }

  @Test
  void 授業が無い単元は_0_回と模試なしで返す() {
    RecordUnitView view = records.getUnit(USER, unitId);

    assertTrue(view.notes().isEmpty());
    assertTrue(view.mockExams().isEmpty());
    assertEquals(0, view.report().lessonCount());
    assertEquals(0, view.report().taughtProblemCount());
    assertNull(view.report().latestMockExam());
  }

  @Test
  void まとめノートの一覧と内容を_返す() throws SQLException {
    UUID recordId = UUID.randomUUID();
    insertLessonRecord(USER, recordId, unitId, UUID.randomUUID(), FIRST, "[\"y = x^2 の頂点\"]");

    assertEquals(
        List.of(recordId),
        records.getUnit(USER, unitId).notes().stream()
            .map(RecordService.NoteEntry::recordId)
            .toList());

    SummaryNoteView note = records.getNote(USER, recordId);
    assertEquals("二次関数", note.unitName());
    assertEquals(FIRST, note.completedAt());
    assertEquals("まとめ", note.summaryText());
    assertEquals(List.of("y = x^2 の頂点"), note.quotes());
  }

  @Test
  void 学習記録を削除すると_記録と理解度と模試の機会の数えが_0_になり_教材は残る() throws SQLException {
    UUID material = insertMaterial(unitId);
    insertLessonRecord(USER, UUID.randomUUID(), unitId, material, FIRST, "[]");
    insertMockExam(unitId, SECOND, 60, "61.0");
    setUnitCounters(unitId, 50, 2);

    records.deleteUnit(USER, unitId);

    RecordUnitView view = records.getUnit(USER, unitId);
    assertEquals(0, view.report().lessonCount());
    assertTrue(view.notes().isEmpty());
    assertTrue(view.mockExams().isEmpty());
    assertNull(view.report().latestMockExam());
    assertEquals(0, queryInt("select understanding from units where id = ?", unitId));
    assertEquals(0, queryInt("select lessons_since_exam from units where id = ?", unitId));
    assertEquals(1, queryInt("select count(*) from materials where id = ?", material));
  }

  @Test
  void 削除は冪等で_記録が無い単元でも成功する() throws SQLException {
    insertLessonRecord(USER, UUID.randomUUID(), unitId, UUID.randomUUID(), FIRST, "[]");

    records.deleteUnit(USER, unitId);
    records.deleteUnit(USER, unitId);

    assertEquals(0, records.getUnit(USER, unitId).report().lessonCount());
    assertTrue(records.listUnits(USER).isEmpty());
  }

  @Test
  void ほかの利用者の単元と記録は_無いものとして扱い_消さない() throws SQLException {
    UUID otherUnit = settings.find(OTHER).orElseThrow().unitId();
    UUID otherRecord = UUID.randomUUID();
    insertLessonRecord(OTHER, otherRecord, otherUnit, UUID.randomUUID(), FIRST, "[]");
    insertMockExam(otherUnit, FIRST, 40, "49.0");

    assertThrows(NotFoundException.class, () -> records.getUnit(USER, otherUnit));
    assertThrows(NotFoundException.class, () -> records.deleteUnit(USER, otherUnit));
    assertThrows(NotFoundException.class, () -> records.getNote(USER, otherRecord));
    assertThrows(NotFoundException.class, () -> records.getUnit(USER, UUID.randomUUID()));

    assertEquals(1, queryInt("select count(*) from lesson_records where unit_id = ?", otherUnit));
    assertEquals(1, queryInt("select count(*) from mock_exams where unit_id = ?", otherUnit));
    assertFalse(records.listUnits(USER).stream().anyMatch(unit -> unit.unitId().equals(otherUnit)));
  }

  @Test
  void 設定が無い利用者は_setup_required() {
    ConflictException error =
        assertThrows(ConflictException.class, () -> records.listUnits(UNKNOWN));

    assertEquals(SettingsService.SETUP_REQUIRED_CODE, error.code());
  }

  private void save(UUID userId, String unitName) {
    settings.save(
        userId,
        new SettingsCommand(
            StudentType.LOGICAL, Subject.MATH, UnitName.parse(unitName).orElseThrow(), 3, true));
  }

  private UUID saveAndFindUnit(UUID userId, String unitName) {
    save(userId, unitName);
    return settings.find(userId).orElseThrow().unitId();
  }

  private static UnitSummary summary(UUID unitId, String unitName, int lessons, int mockExams) {
    return new UnitSummary(unitId, Subject.MATH, unitName, lessons, mockExams);
  }

  private void insertLessonRecord(
      UUID userId,
      UUID recordId,
      UUID unitId,
      UUID materialRef,
      Instant completedAt,
      String quotesJson)
      throws SQLException {
    execute(
        "insert into lesson_records (id, user_id, unit_id, material_ref, completed_at, degrees,"
            + " summary_text, quotes) values (?, ?, ?, ?, ?, '{1,1,1}', 'まとめ', ?::jsonb)",
        recordId,
        userId,
        unitId,
        materialRef,
        OffsetDateTime.ofInstant(completedAt, ZoneOffset.UTC),
        quotesJson);
  }

  private void insertMockExam(UUID unitId, Instant takenAt, int score, String deviation)
      throws SQLException {
    execute(
        "insert into mock_exams (id, unit_id, taken_at, score, deviation, problem_text,"
            + " answer_sheet, student_comment) values (?, ?, ?, ?, ?, '類題', '解答', 'コメント')",
        UUID.randomUUID(),
        unitId,
        OffsetDateTime.ofInstant(takenAt, ZoneOffset.UTC),
        (short) score,
        new BigDecimal(deviation));
  }

  /** 教材の行を入れる。教材の整理の結果は持たない (状態は整理中)。 */
  private UUID insertMaterial(UUID unitId) throws SQLException {
    UUID materialId = UUID.randomUUID();
    execute(
        "insert into materials (id, unit_id, status) values (?, ?, 'organizing')",
        materialId,
        unitId);
    return materialId;
  }

  private void setUnitCounters(UUID unitId, int understanding, int lessonsSinceExam)
      throws SQLException {
    execute(
        "update units set understanding = ?, lessons_since_exam = ? where id = ?",
        (short) understanding,
        (short) lessonsSinceExam,
        unitId);
  }

  private void execute(String sql, Object... params) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, params);
      statement.executeUpdate();
    }
  }

  private int queryInt(String sql, Object... params) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, params);
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        return rows.getInt(1);
      }
    }
  }

  private static void bind(PreparedStatement statement, Object... params) throws SQLException {
    for (int i = 0; i < params.length; i++) {
      statement.setObject(i + 1, params[i]);
    }
  }
}
