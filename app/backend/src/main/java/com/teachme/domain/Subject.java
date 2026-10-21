package com.teachme.domain;

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

  public String id() {
    return id;
  }

  public String displayName() {
    return displayName;
  }
}
