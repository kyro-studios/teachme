package com.teachme.domain;

import java.util.Optional;

/** 単元の名前 (仕様書 5.1)。前後の空白を除いて 1 文字以上 30 文字以内。 */
public final class UnitName {

  /** 単元名の最大の文字数 (前後の空白を除く)。 */
  public static final int MAX_LENGTH = 30;

  private final String value;

  private UnitName(String value) {
    this.value = value;
  }

  /**
   * 前後の空白を除いた単元名を作る。空、または 31 文字以上なら空。
   *
   * <p>文字数は、サロゲートペアを 1 文字として数える。
   */
  public static Optional<UnitName> parse(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String stripped = raw.strip();
    int length = stripped.codePointCount(0, stripped.length());
    if (length < 1 || length > MAX_LENGTH) {
      return Optional.empty();
    }
    return Optional.of(new UnitName(stripped));
  }

  /** 前後の空白を除いた名前。 */
  public String value() {
    return value;
  }
}
