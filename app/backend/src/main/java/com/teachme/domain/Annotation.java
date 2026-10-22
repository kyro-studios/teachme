package com.teachme.domain;

import java.util.Objects;

/**
 * ボードに示す印 (仕様書 5.5、openapi.yaml の `StudentAnnotation`)。AI 生徒が指す箇所か、「？」の印を書く箇所。
 *
 * @param kind 印の種類
 * @param region ボード全体に対する矩形 (0 から 1 の割合)
 */
public record Annotation(Kind kind, OrganizedProblem.Region region) {

  public Annotation {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(region, "region");
  }

  /** 印の種類。`wireName` は API の値と同じ。 */
  public enum Kind {
    POINT("point"),
    QUESTION_MARK("question_mark");

    private final String wireName;

    Kind(String wireName) {
      this.wireName = wireName;
    }

    public String wireName() {
      return wireName;
    }
  }
}
