package com.teachme.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/**
 * 週の範囲。始まりは月曜の 0 時、終わりは次の月曜の 0 時 (日曜の 24 時) で、終わりは含まない (仕様書 1.1)。
 *
 * @param start 週の始まり (含む)
 * @param end 週の終わり (含まない)
 */
public record WeekRange(Instant start, Instant end) {

  public WeekRange {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    if (!start.isBefore(end)) {
      throw new IllegalArgumentException("週の終わりは始まりより後にしてください");
    }
  }

  /**
   * 時刻を含む週を求める。日付は、指定したゾーンで決める (日本時間なら、月曜 0 時は日本時間の月曜 0 時)。
   *
   * @param instant 時刻
   * @param zone 日付を決めるゾーン
   */
  public static WeekRange containing(Instant instant, ZoneId zone) {
    LocalDate day = instant.atZone(zone).toLocalDate();
    LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    return new WeekRange(
        monday.atStartOfDay(zone).toInstant(), monday.plusWeeks(1).atStartOfDay(zone).toInstant());
  }

  /** 時刻が週の範囲に入るか。始まりちょうどは入り、終わりちょうどは入らない。 */
  public boolean contains(Instant instant) {
    return !instant.isBefore(start) && instant.isBefore(end);
  }
}
