package com.teachme.domain;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

/**
 * 今日のモード (仕様書 7.4)。`id` は openapi.yaml の `TodayMode` と同じ値にする。
 *
 * <p>保存されたモードは、保存した日付が今日のときだけ有効とする。日付が変わると、通常に戻る。
 */
public enum TodayMode {
  NORMAL("normal"),
  SELF_STUDY("self_study"),
  HOMEWORK_CHECK("homework_check");

  private final String id;

  TodayMode(String id) {
    this.id = id;
  }

  /** 識別子からモードを求める。知らない識別子なら空。 */
  public static Optional<TodayMode> fromId(String id) {
    return Arrays.stream(values()).filter(mode -> mode.id.equals(id)).findFirst();
  }

  /**
   * 今日に効くモードを求める。保存が無いとき、または保存した日付が今日でないときは、通常とする。
   *
   * @param saved 保存されたモード。無ければ null
   * @param savedDate 保存された日付。無ければ null
   * @param today 今日 (日本時間)
   */
  public static TodayMode effective(TodayMode saved, LocalDate savedDate, LocalDate today) {
    if (saved == null || savedDate == null || !savedDate.equals(today)) {
      return NORMAL;
    }
    return saved;
  }

  public String id() {
    return id;
  }
}
