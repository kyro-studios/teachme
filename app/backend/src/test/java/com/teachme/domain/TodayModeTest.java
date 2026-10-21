package com.teachme.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TodayModeTest {
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

  @Test
  void 保存した日付が今日なら_保存したモードを使う() {
    assertEquals(TodayMode.SELF_STUDY, TodayMode.effective(TodayMode.SELF_STUDY, TODAY, TODAY));
  }

  @Test
  void 保存した日付が今日と違えば_通常に戻す() {
    assertEquals(
        TodayMode.NORMAL, TodayMode.effective(TodayMode.HOMEWORK_CHECK, TODAY.minusDays(1), TODAY));
  }

  @Test
  void 保存が無ければ_通常() {
    assertEquals(TodayMode.NORMAL, TodayMode.effective(null, null, TODAY));
  }

  @Test
  void 識別子からモードを求める() {
    assertEquals(TodayMode.HOMEWORK_CHECK, TodayMode.fromId("homework_check").orElseThrow());
    assertTrue(TodayMode.fromId("unknown").isEmpty());
  }
}
