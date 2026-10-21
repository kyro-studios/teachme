package com.teachme.domain;

import java.util.Arrays;
import java.util.Optional;

/** 教科 (仕様書 1.1)。MVP は数学と理科の 2 教科。`id` は openapi.yaml の `Subject` と同じ値にする。 */
public enum Subject {
  MATH("math", "数学"),
  SCIENCE("science", "理科");

  private final String id;
  private final String displayName;

  Subject(String id, String displayName) {
    this.id = id;
    this.displayName = displayName;
  }

  /** 識別子から教科を求める。知らない識別子なら空。 */
  public static Optional<Subject> fromId(String id) {
    return Arrays.stream(values()).filter(subject -> subject.id.equals(id)).findFirst();
  }

  public String id() {
    return id;
  }

  public String displayName() {
    return displayName;
  }
}
