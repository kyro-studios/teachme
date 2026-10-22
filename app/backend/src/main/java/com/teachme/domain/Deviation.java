package com.teachme.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 偏差値 (仕様書 6.5、アーキテクチャ設計 4.2)。`初期偏差値 + 理解度 ÷ 10` を、小数第 1 位で求める。
 *
 * <p>理解度は整数のため、結果は常に小数第 1 位までの値になる。目標偏差値は使わない (仕様書 1.3)。
 */
public final class Deviation {

  /** 偏差値の小数の桁数。 */
  public static final int SCALE = 1;

  private Deviation() {}

  /**
   * 偏差値。
   *
   * @param studentType 担当する AI 生徒の種類。初期偏差値を使う
   * @param understanding 理解度 (0 から 100)
   * @throws IllegalArgumentException 理解度が 0 から 100 の範囲の外のとき
   */
  public static BigDecimal of(StudentType studentType, int understanding) {
    if (understanding < Understanding.MIN || understanding > Understanding.MAX) {
      throw new IllegalArgumentException("理解度は 0 から 100 の値にしてください");
    }
    return BigDecimal.valueOf(studentType.initialDeviation())
        .add(BigDecimal.valueOf(understanding).divide(BigDecimal.TEN))
        .setScale(SCALE, RoundingMode.HALF_UP);
  }
}
