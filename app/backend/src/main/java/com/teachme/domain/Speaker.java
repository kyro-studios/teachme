package com.teachme.domain;

import java.util.Arrays;

/**
 * 字幕の話し手 (概念モデル 2.3)。利用者、AI 生徒、先輩先生。
 *
 * <p>`wireName` は、DB の `dialogue_turns.speaker` の値と同じ。
 */
public enum Speaker {
  USER("user"),
  STUDENT("student"),
  MENTOR("mentor");

  private final String wireName;

  Speaker(String wireName) {
    this.wireName = wireName;
  }

  public String wireName() {
    return wireName;
  }

  /**
   * @throws IllegalArgumentException 知らない値のとき
   */
  public static Speaker fromWireName(String wireName) {
    return Arrays.stream(values())
        .filter(speaker -> speaker.wireName.equals(wireName))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("知らない話し手です"));
  }
}
