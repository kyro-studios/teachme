package com.teachme.persistence;

import static com.teachme.persistence.generated.Tables.LESSON_RECORDS;
import static com.teachme.persistence.generated.Tables.MOCK_EXAMS;
import static com.teachme.persistence.generated.Tables.UNITS;

import com.teachme.domain.Subject;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;

/**
 * 学習記録 (仕様書 5.8、5.9)。授業の記録 (`lesson_records`)、模試の結果 (`mock_exams`)、単元 (`units`) から集計する。
 *
 * <p>集計の結果は保存しない。単元の学習記録の削除は、単元の行を `for update` で取ってから、1 つのトランザクションで行う (教材は消さない)。
 */
public final class RecordRepository {

  private final DSLContext dsl;

  RecordRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * 記録のある単元 1 件 (一覧の項目)。
   *
   * @param lessonCount 授業の完了数
   * @param mockExamCount 模試の結果の数
   */
  public record UnitSummaryRow(
      UUID unitId, Subject subject, String unitName, int lessonCount, int mockExamCount) {}

  /** 利用者の単元 1 件。 */
  public record UnitRow(UUID unitId, Subject subject, String unitName) {}

  /** まとめノートの一覧の項目。 */
  public record NoteItem(UUID recordId, Instant completedAt) {}

  /** 模試の結果 1 件 (一覧の項目)。 */
  public record MockExamItem(UUID id, Instant takenAt, int score, BigDecimal deviation) {}

  /**
   * 単元の授業の集計。
   *
   * @param lessonCount 授業の完了数の合計
   * @param taughtProblemCount 授業に使った教材の数。同じ教材の重複は 1 つと数える。教材を消しても残る
   */
  public record LessonCounts(int lessonCount, int taughtProblemCount) {}

  /** まとめノート 1 件。 */
  public record NoteDetail(
      UUID recordId,
      String unitName,
      Instant completedAt,
      String summaryText,
      List<String> quotes) {}

  /**
   * 利用者の単元のうち、授業の記録か模試の結果があるもの。並びは単元の作成順。
   *
   * @return 記録の無い単元は含まない。無ければ空
   */
  public List<UnitSummaryRow> unitsWithRecords(UUID userId) {
    Field<Integer> lessonCount =
        DSL.selectCount()
            .from(LESSON_RECORDS)
            .where(LESSON_RECORDS.UNIT_ID.eq(UNITS.ID))
            .asField("lesson_count");
    Field<Integer> mockExamCount =
        DSL.selectCount()
            .from(MOCK_EXAMS)
            .where(MOCK_EXAMS.UNIT_ID.eq(UNITS.ID))
            .asField("mock_exam_count");
    Condition hasRecords =
        DSL.exists(DSL.selectOne().from(LESSON_RECORDS).where(LESSON_RECORDS.UNIT_ID.eq(UNITS.ID)))
            .or(
                DSL.exists(
                    DSL.selectOne().from(MOCK_EXAMS).where(MOCK_EXAMS.UNIT_ID.eq(UNITS.ID))));
    return dsl.select(UNITS.ID, UNITS.SUBJECT, UNITS.NAME, lessonCount, mockExamCount)
        .from(UNITS)
        .where(UNITS.USER_ID.eq(userId))
        .and(hasRecords)
        .orderBy(UNITS.CREATED_AT, UNITS.ID)
        .fetch(
            row ->
                new UnitSummaryRow(
                    row.value1(),
                    Subject.fromId(row.value2()).orElseThrow(),
                    row.value3(),
                    row.value4(),
                    row.value5()));
  }

  /** 利用者の単元。ほかの利用者のもの、無いものは空。 */
  public Optional<UnitRow> findUnit(UUID userId, UUID unitId) {
    return dsl.select(UNITS.ID, UNITS.SUBJECT, UNITS.NAME)
        .from(UNITS)
        .where(UNITS.ID.eq(unitId))
        .and(UNITS.USER_ID.eq(userId))
        .fetchOptional(
            row ->
                new UnitRow(
                    row.value1(), Subject.fromId(row.value2()).orElseThrow(), row.value3()));
  }

  /** 単元のまとめノートの一覧 (新しい順)。 */
  public List<NoteItem> notes(UUID unitId) {
    return dsl.select(LESSON_RECORDS.ID, LESSON_RECORDS.COMPLETED_AT)
        .from(LESSON_RECORDS)
        .where(LESSON_RECORDS.UNIT_ID.eq(unitId))
        .orderBy(LESSON_RECORDS.COMPLETED_AT.desc(), LESSON_RECORDS.ID.desc())
        .fetch(row -> new NoteItem(row.value1(), row.value2().toInstant()));
  }

  /** 単元の模試の結果の一覧 (新しい順)。先頭が最新の模試になる。 */
  public List<MockExamItem> mockExams(UUID unitId) {
    return dsl.select(MOCK_EXAMS.ID, MOCK_EXAMS.TAKEN_AT, MOCK_EXAMS.SCORE, MOCK_EXAMS.DEVIATION)
        .from(MOCK_EXAMS)
        .where(MOCK_EXAMS.UNIT_ID.eq(unitId))
        .orderBy(MOCK_EXAMS.TAKEN_AT.desc(), MOCK_EXAMS.ID.desc())
        .fetch(
            row ->
                new MockExamItem(
                    row.value1(), row.value2().toInstant(), row.value3().intValue(), row.value4()));
  }

  /** 単元の授業の集計。授業が無ければ、どちらも 0。 */
  public LessonCounts lessonCounts(UUID unitId) {
    return dsl.select(DSL.count(), DSL.countDistinct(LESSON_RECORDS.MATERIAL_REF))
        .from(LESSON_RECORDS)
        .where(LESSON_RECORDS.UNIT_ID.eq(unitId))
        .fetchSingle(row -> new LessonCounts(row.value1(), row.value2()));
  }

  /** 利用者のまとめノート。ほかの利用者のもの、無いものは空。 */
  public Optional<NoteDetail> findNote(UUID userId, UUID recordId) {
    return dsl.select(
            LESSON_RECORDS.ID,
            UNITS.NAME,
            LESSON_RECORDS.COMPLETED_AT,
            LESSON_RECORDS.SUMMARY_TEXT,
            LESSON_RECORDS.QUOTES)
        .from(LESSON_RECORDS)
        .join(UNITS)
        .on(UNITS.ID.eq(LESSON_RECORDS.UNIT_ID))
        .where(LESSON_RECORDS.ID.eq(recordId))
        .and(LESSON_RECORDS.USER_ID.eq(userId))
        .fetchOptional(
            row ->
                new NoteDetail(
                    row.value1(),
                    row.value2(),
                    row.value3().toInstant(),
                    row.value4(),
                    LessonRecordRepository.fromJsonQuotes(row.value5())));
  }

  /**
   * 単元の学習記録を消す。授業の記録、まとめノート、模試の結果を消し、理解度と模試の機会の数えを 0 に戻す。教材は残す。1 つのトランザクションで行う。
   *
   * <p>記録が無くても、利用者の単元なら消す操作は成功する (削除は冪等)。
   *
   * @return 利用者の単元のとき true。ほかの利用者の単元、無い単元のときは何もせず false
   */
  public boolean deleteUnitRecords(UUID userId, UUID unitId) {
    return dsl.transactionResult(
        configuration -> {
          DSLContext tx = DSL.using(configuration);
          boolean owned =
              tx.selectOne()
                  .from(UNITS)
                  .where(UNITS.ID.eq(unitId))
                  .and(UNITS.USER_ID.eq(userId))
                  .forUpdate()
                  .fetchOptional()
                  .isPresent();
          if (!owned) {
            return false;
          }
          tx.deleteFrom(LESSON_RECORDS).where(LESSON_RECORDS.UNIT_ID.eq(unitId)).execute();
          tx.deleteFrom(MOCK_EXAMS).where(MOCK_EXAMS.UNIT_ID.eq(unitId)).execute();
          tx.update(UNITS)
              .set(UNITS.UNDERSTANDING, (short) 0)
              .set(UNITS.LESSONS_SINCE_EXAM, (short) 0)
              .where(UNITS.ID.eq(unitId))
              .execute();
          return true;
        });
  }
}
