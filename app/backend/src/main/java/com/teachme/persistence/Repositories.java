package com.teachme.persistence;

import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

/** jOOQ を使うリポジトリの組み立て。bootstrap は、jOOQ に触れずに、ここからリポジトリを受け取る。 */
public final class Repositories {

  private final SettingsRepository settings;
  private final TodayModeRepository todayModes;
  private final LessonRecordRepository lessonRecords;
  private final UnitRepository units;
  private final MaterialRepository materials;

  public Repositories(DataSource dataSource) {
    DSLContext dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    this.settings = new SettingsRepository(dsl);
    this.todayModes = new TodayModeRepository(dsl);
    this.lessonRecords = new LessonRecordRepository(dsl);
    this.units = new UnitRepository(dsl);
    this.materials = new MaterialRepository(dsl);
  }

  public SettingsRepository settings() {
    return settings;
  }

  public TodayModeRepository todayModes() {
    return todayModes;
  }

  public LessonRecordRepository lessonRecords() {
    return lessonRecords;
  }

  public UnitRepository units() {
    return units;
  }

  public MaterialRepository materials() {
    return materials;
  }
}
