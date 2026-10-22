package com.teachme.domain;

/**
 * 理解度 (仕様書 6.3、アーキテクチャ設計 4.2)。単元ごとの、AI 生徒の理解の度合い。0 以上 100 以下の整数で、初期値は 0。
 *
 * <p>授業の記録が確定したときに、授業の得点で更新する。学習記録の削除を除き、減らさない。
 */
public final class Understanding {

  public static final int MIN = 0;

  public static final int MAX = 100;

  /** 授業の得点 1 点あたりの、理解度の増え。 */
  public static final int POINTS_PER_SCORE = 5;

  private Understanding() {}

  /**
   * 授業の記録の後の理解度。`min(100, 前の値 + 得点 × 5)`。
   *
   * @param previous 前の理解度 (0 から 100)
   * @throws IllegalArgumentException 前の理解度が 0 から 100 の範囲の外のとき
   */
  public static int after(int previous, LessonScore score) {
    if (previous < MIN || previous > MAX) {
      throw new IllegalArgumentException("理解度は 0 から 100 の値にしてください");
    }
    return Math.min(MAX, previous + score.value() * POINTS_PER_SCORE);
  }
}
