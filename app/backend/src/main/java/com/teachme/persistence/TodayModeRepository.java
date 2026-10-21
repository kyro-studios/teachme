package com.teachme.persistence;

import static com.teachme.persistence.generated.Tables.TODAY_MODES;

import com.teachme.domain.TodayMode;
import java.time.LocalDate;
import java.util.UUID;
import org.jooq.DSLContext;

/** 今日のモード (`today_modes`)。日付が今日でない保存は、通常として読む。 */
public final class TodayModeRepository {

  private final DSLContext dsl;

  TodayModeRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** 今日に効くモード。保存が無いとき、または保存した日付が今日でないときは、通常。 */
  public TodayMode effectiveMode(UUID userId, LocalDate today) {
    return dsl.select(TODAY_MODES.MODE, TODAY_MODES.MODE_DATE)
        .from(TODAY_MODES)
        .where(TODAY_MODES.USER_ID.eq(userId))
        .fetchOptional(
            row ->
                TodayMode.effective(
                    TodayMode.fromId(row.get(TODAY_MODES.MODE)).orElseThrow(),
                    row.get(TODAY_MODES.MODE_DATE),
                    today))
        .orElse(TodayMode.NORMAL);
  }

  /** 今日のモードを保存する。`date` は今日 (日本時間)。 */
  public void save(UUID userId, TodayMode mode, LocalDate date) {
    dsl.insertInto(TODAY_MODES)
        .set(TODAY_MODES.USER_ID, userId)
        .set(TODAY_MODES.MODE, mode.id())
        .set(TODAY_MODES.MODE_DATE, date)
        .onConflict(TODAY_MODES.USER_ID)
        .doUpdate()
        .set(TODAY_MODES.MODE, mode.id())
        .set(TODAY_MODES.MODE_DATE, date)
        .execute();
  }
}
