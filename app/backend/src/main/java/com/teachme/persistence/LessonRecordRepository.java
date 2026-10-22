package com.teachme.persistence;

import static com.teachme.persistence.generated.Tables.LESSONS;
import static com.teachme.persistence.generated.Tables.LESSON_RECORDS;
import static com.teachme.persistence.generated.Tables.UNITS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teachme.domain.Degrees;
import com.teachme.domain.LessonScore;
import com.teachme.domain.Understanding;
import com.teachme.domain.WeekRange;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;

/**
 * 授業の記録 (`lesson_records`) と、授業の記録の確定 (`lessons`、`units` の更新を含む)。週の数えも行う。
 *
 * <p>授業の記録の確定は、授業の行を `for update` で取ってから書く。中断 (授業の行の削除) と並んだときは、どちらか一方だけが結果を残す (アーキテクチャ設計 4.4)。
 */
public final class LessonRecordRepository {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  private final DSLContext dsl;

  LessonRecordRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * 授業の記録 1 件 (まとめノートを含む)。
   *
   * @param id 授業の識別子。授業の記録の識別子と同じ値
   * @param quotes 引用する板書の文章。無ければ空
   */
  public record RecordedLesson(
      UUID id, Instant completedAt, String summaryText, List<String> quotes) {}

  /** 利用者が、週の範囲に完了した授業の回数。全単元を合わせる。 */
  public int countCompletedIn(UUID userId, WeekRange week) {
    return dsl.selectCount()
        .from(LESSON_RECORDS)
        .where(LESSON_RECORDS.USER_ID.eq(userId))
        .and(
            LESSON_RECORDS.COMPLETED_AT.greaterOrEqual(
                OffsetDateTime.ofInstant(week.start(), ZoneOffset.UTC)))
        .and(
            LESSON_RECORDS.COMPLETED_AT.lessThan(
                OffsetDateTime.ofInstant(week.end(), ZoneOffset.UTC)))
        .fetchSingle()
        .value1();
  }

  /** 利用者の授業の記録。無ければ (ほかの利用者のもの、未確定の授業も含む) 空。 */
  public Optional<RecordedLesson> find(UUID userId, UUID lessonId) {
    return dsl.select(
            LESSON_RECORDS.ID,
            LESSON_RECORDS.COMPLETED_AT,
            LESSON_RECORDS.SUMMARY_TEXT,
            LESSON_RECORDS.QUOTES)
        .from(LESSON_RECORDS)
        .where(LESSON_RECORDS.ID.eq(lessonId))
        .and(LESSON_RECORDS.USER_ID.eq(userId))
        .fetchOptional(
            row ->
                new RecordedLesson(
                    row.value1(),
                    row.value2().toInstant(),
                    row.value3(),
                    fromJsonQuotes(row.value4())));
  }

  /**
   * 授業を記録する。授業の記録とまとめノートを入れ、理解度と模試の機会の数えを更新し、授業の行を消す。1 つのトランザクションで行う。
   *
   * <p>授業の行が無ければ (中断された後、または同時に確定済み) 何もせず空を返す。
   *
   * @param summaryText まとめ文
   * @param quotes 引用する板書の文章
   * @return 記録した授業。授業の行が無ければ空
   */
  public Optional<RecordedLesson> complete(
      UUID userId, UUID lessonId, String summaryText, List<String> quotes, Instant completedAt) {
    Instant recordedAt = completedAt.truncatedTo(ChronoUnit.MICROS);
    return dsl.transactionResult(
        configuration -> {
          DSLContext tx = DSL.using(configuration);
          Record lesson =
              tx.select(LESSONS.UNIT_ID, LESSONS.MATERIAL_ID, LESSONS.DEGREES)
                  .from(LESSONS)
                  .where(LESSONS.ID.eq(lessonId))
                  .and(LESSONS.USER_ID.eq(userId))
                  .forUpdate()
                  .fetchOne();
          if (lesson == null) {
            return Optional.empty();
          }
          UUID unitId = lesson.get(LESSONS.UNIT_ID);
          Degrees degrees = toDegrees(lesson.get(LESSONS.DEGREES));
          int understanding =
              tx.select(UNITS.UNDERSTANDING)
                  .from(UNITS)
                  .where(UNITS.ID.eq(unitId))
                  .forUpdate()
                  .fetchSingle()
                  .value1()
                  .intValue();
          tx.update(UNITS)
              .set(
                  UNITS.UNDERSTANDING,
                  (short) Understanding.after(understanding, LessonScore.of(degrees)))
              .set(UNITS.LESSONS_SINCE_EXAM, UNITS.LESSONS_SINCE_EXAM.plus(1))
              .where(UNITS.ID.eq(unitId))
              .execute();
          tx.insertInto(LESSON_RECORDS)
              .set(LESSON_RECORDS.ID, lessonId)
              .set(LESSON_RECORDS.USER_ID, userId)
              .set(LESSON_RECORDS.UNIT_ID, unitId)
              .set(LESSON_RECORDS.MATERIAL_REF, lesson.get(LESSONS.MATERIAL_ID))
              .set(LESSON_RECORDS.COMPLETED_AT, toOffsetDateTime(recordedAt))
              .set(LESSON_RECORDS.DEGREES, toSmallints(degrees))
              .set(LESSON_RECORDS.SUMMARY_TEXT, summaryText)
              .set(LESSON_RECORDS.QUOTES, toJsonb(quotes))
              .execute();
          // 字幕は、授業の行と連鎖して消える
          tx.deleteFrom(LESSONS).where(LESSONS.ID.eq(lessonId)).execute();
          return Optional.of(new RecordedLesson(lessonId, recordedAt, summaryText, quotes));
        });
  }

  private static Degrees toDegrees(Short[] stored) {
    return new Degrees(Arrays.stream(stored).map(Short::intValue).toList());
  }

  private static Short[] toSmallints(Degrees degrees) {
    return degrees.values().stream().map(value -> (short) value.intValue()).toArray(Short[]::new);
  }

  private static JSONB toJsonb(List<String> quotes) {
    try {
      return JSONB.valueOf(JSON.writeValueAsString(quotes));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("引用する板書を JSON にできません", e);
    }
  }

  private static List<String> fromJsonQuotes(JSONB quotes) {
    try {
      return JSON.readValue(quotes.data(), STRING_LIST);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("保存された引用する板書を読めません", e);
    }
  }

  private static OffsetDateTime toOffsetDateTime(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
