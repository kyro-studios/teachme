package com.teachme.domain;

/**
 * 授業の得点 (仕様書 6.2、アーキテクチャ設計 4.2)。記録の確定時の、指導のポイント 3 件の度合いの合計。0 以上 6 以下の整数。
 *
 * @param value 得点
 */
public record LessonScore(int value) {

  /** 得点の上限。指導のポイント 3 件それぞれの度合いの上限を足したもの。 */
  public static final int MAX = Degrees.COUNT * Degrees.MAX;

  public LessonScore {
    if (value < 0 || value > MAX) {
      throw new IllegalArgumentException("授業の得点は 0 から 6 の値にしてください");
    }
  }

  /** 度合いの合計を、授業の得点として求める。 */
  public static LessonScore of(Degrees degrees) {
    return new LessonScore(degrees.values().stream().mapToInt(Integer::intValue).sum());
  }
}
