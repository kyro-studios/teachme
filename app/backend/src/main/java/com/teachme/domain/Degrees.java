package com.teachme.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 指導のポイントの度合い (仕様書 6.1、アーキテクチャ設計 4.2)。指導のポイント 3 件の、それぞれ 0 から 2 の値。
 *
 * <p>発話の学習評価の結果は、授業の中で最大値として取り込む ({@link #mergeMax})。下がることは無い。
 *
 * @param values 指導のポイントの順に並ぶ度合い (3 件)
 */
public record Degrees(List<Integer> values) {

  /** 指導のポイントの件数。 */
  public static final int COUNT = 3;

  /** 度合いの下限と上限。 */
  public static final int MIN = 0;

  public static final int MAX = 2;

  public Degrees {
    if (values == null || values.size() != COUNT) {
      throw new IllegalArgumentException("度合いは指導のポイントと同じ 3 件にしてください");
    }
    for (Integer value : values) {
      if (value == null || value < MIN || value > MAX) {
        throw new IllegalArgumentException("度合いは 0 から 2 の値にしてください");
      }
    }
    values = List.copyOf(values);
  }

  /** 3 件とも 0 の、授業の初めの度合い。 */
  public static Degrees initial() {
    return new Degrees(List.of(0, 0, 0));
  }

  /**
   * 指導のポイントごとに、大きいほうの値を取った度合いを返す。
   *
   * @param other 取り込む度合い (3 件)
   */
  public Degrees mergeMax(Degrees other) {
    List<Integer> merged = new ArrayList<>(COUNT);
    for (int i = 0; i < COUNT; i++) {
      merged.add(Math.max(values.get(i), other.values.get(i)));
    }
    return new Degrees(merged);
  }
}
