package com.teachme.domain;

/** 模試の機会 (仕様書 7.5)。授業の完了が一定の回数に達すると、挑戦できる。挑戦して結果が保存されると、数え直す。 */
public final class MockExamOpportunity {

  /** 模試の機会ができる、授業の完了の回数。 */
  public static final int LESSONS_PER_OPPORTUNITY = 3;

  private MockExamOpportunity() {}

  /**
   * 模試の機会があるか。
   *
   * @param lessonsSinceExam 前回の模試の結果の保存から (最初は最初から) の授業の完了数
   */
  public static boolean isAvailable(int lessonsSinceExam) {
    return lessonsSinceExam >= LESSONS_PER_OPPORTUNITY;
  }
}
