package com.teachme.persistence;

import static com.teachme.persistence.generated.Tables.LESSON_RECORDS;

import com.teachme.domain.WeekRange;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jooq.DSLContext;

/** 授業の記録 (`lesson_records`) の、週の数え。 */
public final class LessonRecordRepository {

  private final DSLContext dsl;

  LessonRecordRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

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
}
