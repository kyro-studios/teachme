package com.teachme.persistence;

import static com.teachme.persistence.generated.Tables.MATERIALS;
import static com.teachme.persistence.generated.Tables.UNITS;

import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/** 単元 (`units`) の、模試の機会の数え。と、単元に整理済みの教材があるか (`materials`)。 */
public final class UnitRepository {

  private static final String ORGANIZED = "organized";

  private final DSLContext dsl;

  UnitRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** 前回の模試の結果の保存から (最初は最初から) の授業の完了数。 */
  public int lessonsSinceExam(UUID unitId) {
    return dsl.select(UNITS.LESSONS_SINCE_EXAM)
        .from(UNITS)
        .where(UNITS.ID.eq(unitId))
        .fetchSingle()
        .value1()
        .intValue();
  }

  /** 単元に、整理済みの教材が 1 つ以上あるか。 */
  public boolean hasOrganizedMaterial(UUID unitId) {
    return dsl.fetchExists(
        DSL.selectOne()
            .from(MATERIALS)
            .where(MATERIALS.UNIT_ID.eq(unitId))
            .and(MATERIALS.STATUS.eq(ORGANIZED)));
  }
}
