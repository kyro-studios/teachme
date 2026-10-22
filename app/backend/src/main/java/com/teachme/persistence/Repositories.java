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
  private final DialogueRepository dialogues;
  private final MaterialRepository materials;
  private final MockExamRepository mockExams;

  public Repositories(DataSource dataSource) {
    DSLContext dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    this.settings = new SettingsRepository(dsl);
    this.todayModes = new TodayModeRepository(dsl);
    this.lessonRecords = new LessonRecordRepository(dsl);
    this.units = new UnitRepository(dsl);
    this.dialogues = new DialogueRepository(dsl);
    this.materials = new MaterialRepository(dsl);
    this.mockExams = new MockExamRepository(dsl);
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

  public DialogueRepository dialogues() {
    return dialogues;
  }

  public MaterialRepository materials() {
    return materials;
  }

  public MockExamRepository mockExams() {
    return mockExams;
  }
}
