package com.teachme.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class UnderstandingTest {

  @Test
  void 得点_6_の授業は_理解度を_30_上げる() {
    LessonScore score = LessonScore.of(new Degrees(List.of(2, 2, 2)));

    assertEquals(30, Understanding.after(0, score));
  }

  @Test
  void 得点_1_の授業は_理解度を_5_上げる() {
    assertEquals(5, Understanding.after(0, new LessonScore(1)));
  }

  @Test
  void 理解度は_100_を超えない() {
    assertEquals(100, Understanding.after(90, new LessonScore(6)));
    assertEquals(100, Understanding.after(100, new LessonScore(0)));
  }

  @Test
  void 得点_0_の授業は_理解度を減らさない() {
    assertEquals(40, Understanding.after(40, new LessonScore(0)));
  }

  @Test
  void 前の理解度が_0_から_100_の外なら_IllegalArgumentException() {
    LessonScore score = new LessonScore(1);

    assertThrows(IllegalArgumentException.class, () -> Understanding.after(-1, score));
    assertThrows(IllegalArgumentException.class, () -> Understanding.after(101, score));
  }
}
