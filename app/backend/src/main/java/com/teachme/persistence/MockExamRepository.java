package com.teachme.persistence;

import static com.teachme.persistence.generated.Tables.LESSON_RECORDS;
import static com.teachme.persistence.generated.Tables.MOCK_EXAMS;
import static com.teachme.persistence.generated.Tables.UNITS;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

/**
 * 模試の結果 (`mock_exams`) と、模試の挑戦 (単元の行のロックと、模試の機会の数え)。
 *
 * <p>挑戦は、単元の行を `for update nowait` で取り、結果の作成 (外部 AI の呼び出し) と保存を、同じトランザクションで行う。同時の挑戦は、後の側が取れずに
 * 空を返す。作成に失敗すると、何も保存されず、模試の機会も残る (仕様書 5.7、アーキテクチャ設計 4.4)。
 */
public final class MockExamRepository {

  /** PostgreSQL の `lock_not_available` (`nowait` で行を取れなかった)。 */
  private static final String LOCK_NOT_AVAILABLE = "55P03";

  private final DSLContext dsl;

  MockExamRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * ロック中の単元の状態。
   *
   * @param understanding 理解度 (挑戦した時点の値。模試の得点になる)
   * @param lessonsSinceExam 前回の模試の結果の保存から (最初は最初から) の授業の完了数
   */
  public record LockedUnit(int understanding, int lessonsSinceExam) {}

  /**
   * 保存する模試の結果。
   *
   * @param deviation 偏差値 (小数第 1 位)
   */
  public record NewMockExam(
      UUID id,
      Instant takenAt,
      int score,
      BigDecimal deviation,
      String problemText,
      String answerSheet,
      String studentComment) {}

  /**
   * 模試の結果 1 件。単元の名前を含む。
   *
   * @param unitName 単元の名前
   */
  public record MockExamRow(
      UUID id,
      UUID unitId,
      String unitName,
      Instant takenAt,
      int score,
      BigDecimal deviation,
      String problemText,
      String answerSheet,
      String studentComment) {}

  /** 偏差値の推移の 1 点。 */
  public record HistoryPoint(Instant takenAt, BigDecimal deviation) {}

  /**
   * 模試に挑戦する。単元の行を `for update nowait` で取り、`challenge` で結果を作って保存し、模試の機会を消す。1 つのトランザクションで行う。
   *
   * <p>`challenge` が例外を投げたときは、何も保存せず、その例外をそのまま投げる。
   *
   * @param unitId 挑戦する単元 (利用者の単元であること)
   * @param challenge ロック中の単元の状態から、保存する結果を作る (外部 AI の呼び出しを含む)
   * @return 保存した結果。ほかの挑戦が進行中で単元の行を取れなかったときは空
   */
  public Optional<NewMockExam> challenge(UUID unitId, Function<LockedUnit, NewMockExam> challenge) {
    try {
      return Optional.of(
          dsl.transactionResult(
              configuration -> {
                DSLContext tx = DSL.using(configuration);
                Record2<Short, Short> unit =
                    tx.select(UNITS.UNDERSTANDING, UNITS.LESSONS_SINCE_EXAM)
                        .from(UNITS)
                        .where(UNITS.ID.eq(unitId))
                        .forUpdate()
                        .noWait()
                        .fetchSingle();
                NewMockExam exam =
                    challenge.apply(
                        new LockedUnit(unit.value1().intValue(), unit.value2().intValue()));
                tx.insertInto(MOCK_EXAMS)
                    .set(MOCK_EXAMS.ID, exam.id())
                    .set(MOCK_EXAMS.UNIT_ID, unitId)
                    .set(MOCK_EXAMS.TAKEN_AT, toOffsetDateTime(exam.takenAt()))
                    .set(MOCK_EXAMS.SCORE, (short) exam.score())
                    .set(MOCK_EXAMS.DEVIATION, exam.deviation())
                    .set(MOCK_EXAMS.PROBLEM_TEXT, exam.problemText())
                    .set(MOCK_EXAMS.ANSWER_SHEET, exam.answerSheet())
                    .set(MOCK_EXAMS.STUDENT_COMMENT, exam.studentComment())
                    .execute();
                tx.update(UNITS)
                    .set(UNITS.LESSONS_SINCE_EXAM, (short) 0)
                    .where(UNITS.ID.eq(unitId))
                    .execute();
                return exam;
              }));
    } catch (DataAccessException e) {
      if (LOCK_NOT_AVAILABLE.equals(e.sqlState())) {
        return Optional.empty();
      }
      throw e;
    }
  }

  /** 利用者の模試の結果。ほかの利用者のもの、無いものは空。 */
  public Optional<MockExamRow> find(UUID userId, UUID mockExamId) {
    return dsl.select(
            MOCK_EXAMS.ID,
            MOCK_EXAMS.UNIT_ID,
            UNITS.NAME,
            MOCK_EXAMS.TAKEN_AT,
            MOCK_EXAMS.SCORE,
            MOCK_EXAMS.DEVIATION,
            MOCK_EXAMS.PROBLEM_TEXT,
            MOCK_EXAMS.ANSWER_SHEET,
            MOCK_EXAMS.STUDENT_COMMENT)
        .from(MOCK_EXAMS)
        .join(UNITS)
        .on(UNITS.ID.eq(MOCK_EXAMS.UNIT_ID))
        .where(MOCK_EXAMS.ID.eq(mockExamId))
        .and(UNITS.USER_ID.eq(userId))
        .fetchOptional(
            row ->
                new MockExamRow(
                    row.value1(),
                    row.value2(),
                    row.value3(),
                    row.value4().toInstant(),
                    row.value5().intValue(),
                    row.value6(),
                    row.value7(),
                    row.value8(),
                    row.value9()));
  }

  /** 単元の模試の結果の偏差値の推移。古い順 (`taken_at`、同じときは識別子)。 */
  public List<HistoryPoint> history(UUID unitId) {
    return dsl.select(MOCK_EXAMS.TAKEN_AT, MOCK_EXAMS.DEVIATION)
        .from(MOCK_EXAMS)
        .where(MOCK_EXAMS.UNIT_ID.eq(unitId))
        .orderBy(MOCK_EXAMS.TAKEN_AT, MOCK_EXAMS.ID)
        .fetch(row -> new HistoryPoint(row.value1().toInstant(), row.value2()));
  }

  /**
   * 単元の直近の授業が使った教材の識別子 (`lesson_records.material_ref`)。授業の記録が無ければ空。
   *
   * <p>教材を削除しても、この識別子は残る。教材の有無は、呼び出し側が教材の表で確かめる。
   */
  public Optional<UUID> latestLessonMaterialRef(UUID unitId) {
    return dsl.select(LESSON_RECORDS.MATERIAL_REF)
        .from(LESSON_RECORDS)
        .where(LESSON_RECORDS.UNIT_ID.eq(unitId))
        .orderBy(LESSON_RECORDS.COMPLETED_AT.desc(), LESSON_RECORDS.ID.desc())
        .limit(1)
        .fetchOptional(row -> row.value1());
  }

  private static OffsetDateTime toOffsetDateTime(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
