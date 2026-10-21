package com.teachme.domain;

/** 対話の種類。授業と、先輩先生への相談 (仕様書 5.4、5.5)。進行の仕組みは共通で、上限だけが違う (アーキテクチャ設計 4.4)。 */
public enum DialogueKind {
  /** 授業: 20 往復、600 秒。 */
  LESSON(DialogueLimits.lesson()),

  /** 先輩先生への相談: 15 往復、600 秒。 */
  CONSULTATION(DialogueLimits.consultation());

  private final DialogueLimits limits;

  DialogueKind(DialogueLimits limits) {
    this.limits = limits;
  }

  public DialogueLimits limits() {
    return limits;
  }
}
