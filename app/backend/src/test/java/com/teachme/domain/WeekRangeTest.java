package com.teachme.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class WeekRangeTest {
  private static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");

  /** 2026-10-19 (月) 0 時から、2026-10-26 (月) 0 時の手前まで。日本時間。 */
  private static final WeekRange WEEK =
      WeekRange.containing(ZonedDateTime.of(2026, 10, 21, 10, 0, 0, 0, JAPAN).toInstant(), JAPAN);

  private static Instant jst(int month, int day, int hour, int minute, int second, int nanos) {
    return ZonedDateTime.of(2026, month, day, hour, minute, second, nanos, JAPAN).toInstant();
  }

  @Test
  void 水曜の時刻を含む週は_月曜_0_時から_次の月曜_0_時まで() {
    assertEquals(jst(10, 19, 0, 0, 0, 0), WEEK.start());
    assertEquals(jst(10, 26, 0, 0, 0, 0), WEEK.end());
  }

  @Test
  void 週の始まりは_日本時間の月曜_0_時で_UTC_の日曜_15_時も含む() {
    // 2026-10-18T15:00Z は、日本時間の 2026-10-19 0:00 にあたる
    assertEquals(
        Instant.parse("2026-10-18T15:00:00Z"),
        WeekRange.containing(Instant.parse("2026-10-18T15:00:00Z"), JAPAN).start());
    assertFalse(WEEK.contains(Instant.parse("2026-10-18T14:59:59.999999999Z")));
  }

  @Test
  void 月曜_0_時ちょうどは含み_その直前の日曜の終わりは含まない() {
    assertTrue(WEEK.contains(jst(10, 19, 0, 0, 0, 0)));
    assertFalse(WEEK.contains(jst(10, 18, 23, 59, 59, 999_999_999)));
  }

  @Test
  void 日曜の_24_時の手前は含み_次の月曜_0_時は含まない() {
    assertTrue(WEEK.contains(jst(10, 25, 23, 59, 59, 999_000_000)));
    assertFalse(WEEK.contains(jst(10, 26, 0, 0, 0, 0)));
  }

  @Test
  void 日曜の_0_時は同じ週に入る() {
    assertTrue(WEEK.contains(jst(10, 25, 0, 0, 0, 0)));
  }

  @Test
  void 月曜の時刻を指定すると_その日から始まる週になる() {
    WeekRange week = WeekRange.containing(jst(10, 19, 0, 0, 0, 0), JAPAN);

    assertEquals(jst(10, 19, 0, 0, 0, 0), week.start());
  }
}
