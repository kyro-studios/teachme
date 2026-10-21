package com.teachme.persistence;

import static com.teachme.persistence.generated.Tables.UNITS;
import static com.teachme.persistence.generated.Tables.USERS;
import static com.teachme.persistence.generated.Tables.USER_SETTINGS;

import com.teachme.domain.StudentType;
import com.teachme.domain.Subject;
import com.teachme.domain.UnitName;
import com.teachme.domain.UserSettings;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/** 利用者 (`users`)、設定 (`user_settings`)、単元 (`units`) の保存と取得。 */
public final class SettingsRepository {

  private final DSLContext dsl;

  SettingsRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** 設定が完了している利用者の設定。未完了なら空。 */
  public Optional<UserSettings> find(UUID userId) {
    return dsl.select(
            USER_SETTINGS.STUDENT_TYPE,
            USER_SETTINGS.LESSONS_PER_WEEK,
            UNITS.ID,
            UNITS.SUBJECT,
            UNITS.NAME)
        .from(USER_SETTINGS)
        .join(UNITS)
        .on(UNITS.ID.eq(USER_SETTINGS.CURRENT_UNIT_ID))
        .where(USER_SETTINGS.USER_ID.eq(userId))
        .fetchOptional(
            row ->
                new UserSettings(
                    StudentType.fromId(row.get(USER_SETTINGS.STUDENT_TYPE)).orElseThrow(),
                    Subject.fromId(row.get(UNITS.SUBJECT)).orElseThrow(),
                    row.get(UNITS.ID),
                    row.get(UNITS.NAME),
                    row.get(USER_SETTINGS.LESSONS_PER_WEEK).intValue()));
  }

  /**
   * 設定を保存し、完了済みにする。利用者の行と、同じ教科の同名の単元が無ければ作る。すべて 1 つのトランザクションで行う。
   *
   * @param updatedAt 保存した時刻
   * @return 保存した設定 (使った単元の識別子を含む)
   */
  public UserSettings save(
      UUID userId,
      StudentType studentType,
      Subject subject,
      UnitName unitName,
      int lessonsPerWeek,
      Instant updatedAt) {
    OffsetDateTime updated = OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC);
    return dsl.transactionResult(
        configuration -> {
          DSLContext tx = DSL.using(configuration);
          tx.insertInto(USERS).set(USERS.ID, userId).onConflict(USERS.ID).doNothing().execute();
          // 同じ教科に同名の単元があれば、新しく作らず、その単元を使う
          tx.insertInto(UNITS)
              .set(UNITS.ID, UUID.randomUUID())
              .set(UNITS.USER_ID, userId)
              .set(UNITS.SUBJECT, subject.id())
              .set(UNITS.NAME, unitName.value())
              .onConflict(UNITS.USER_ID, UNITS.SUBJECT, UNITS.NAME)
              .doNothing()
              .execute();
          UUID unitId =
              tx.select(UNITS.ID)
                  .from(UNITS)
                  .where(UNITS.USER_ID.eq(userId))
                  .and(UNITS.SUBJECT.eq(subject.id()))
                  .and(UNITS.NAME.eq(unitName.value()))
                  .fetchSingle()
                  .get(UNITS.ID);
          tx.insertInto(USER_SETTINGS)
              .set(USER_SETTINGS.USER_ID, userId)
              .set(USER_SETTINGS.STUDENT_TYPE, studentType.id())
              .set(USER_SETTINGS.CURRENT_UNIT_ID, unitId)
              .set(USER_SETTINGS.LESSONS_PER_WEEK, (short) lessonsPerWeek)
              .set(USER_SETTINGS.PRIVACY_CONFIRMED, true)
              .set(USER_SETTINGS.UPDATED_AT, updated)
              .onConflict(USER_SETTINGS.USER_ID)
              .doUpdate()
              .set(USER_SETTINGS.STUDENT_TYPE, studentType.id())
              .set(USER_SETTINGS.CURRENT_UNIT_ID, unitId)
              .set(USER_SETTINGS.LESSONS_PER_WEEK, (short) lessonsPerWeek)
              .set(USER_SETTINGS.PRIVACY_CONFIRMED, true)
              .set(USER_SETTINGS.UPDATED_AT, updated)
              .execute();
          return new UserSettings(studentType, subject, unitId, unitName.value(), lessonsPerWeek);
        });
  }
}
