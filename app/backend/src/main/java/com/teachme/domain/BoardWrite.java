package com.teachme.domain;

import java.util.Objects;

/**
 * 先輩先生がボードに書き込む内容 (仕様書 5.4、openapi.yaml の `BoardWrite`)。図や式の文字の書き込みと、範囲の強調がある。
 *
 * @param kind 書き込みの種類
 * @param text 文字の書き込みの内容。範囲の強調では無し (null)
 * @param region ボード全体に対する矩形 (0 から 1 の割合)。無ければ null
 */
public record BoardWrite(Kind kind, String text, OrganizedProblem.Region region) {

  public BoardWrite {
    Objects.requireNonNull(kind, "kind");
  }

  /** 書き込みの種類。`wireName` は API の値と同じ。 */
  public enum Kind {
    TEXT("text"),
    HIGHLIGHT("highlight");

    private final String wireName;

    Kind(String wireName) {
      this.wireName = wireName;
    }

    public String wireName() {
      return wireName;
    }
  }
}
