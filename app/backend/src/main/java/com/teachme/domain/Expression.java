package com.teachme.domain;

import java.util.Arrays;

/**
 * AI 生徒の表情 (仕様書 5.5、openapi.yaml の `Expression`)。「考え中」は相槌のときだけクライアントが出すため、含めない。
 *
 * <p>`wireName` は、DB の `dialogue_turns.expression` と API の値と同じ。
 */
public enum Expression {
  NORMAL("normal"),
  TROUBLED("troubled"),
  CONVINCED("convinced");

  private final String wireName;

  Expression(String wireName) {
    this.wireName = wireName;
  }

  public String wireName() {
    return wireName;
  }

  /**
   * @throws IllegalArgumentException 知らない値のとき
   */
  public static Expression fromWireName(String wireName) {
    return Arrays.stream(values())
        .filter(expression -> expression.wireName.equals(wireName))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("知らない表情です"));
  }
}
