package com.teachme.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LessonScoreTest {

  @Test
  void 得点は_3_件の度合いの合計になる() {
    assertEquals(3, LessonScore.of(new Degrees(List.of(2, 1, 0))).value());
  }

  @Test
  void 全部_2_なら_得点は_6() {
    assertEquals(6, LessonScore.of(new Degrees(List.of(2, 2, 2))).value());
  }

  @Test
  void 全部_0_なら_得点は_0() {
    assertEquals(0, LessonScore.of(Degrees.initial()).value());
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 7})
  void 得点は_0_から_6_の外なら_IllegalArgumentException(int value) {
    assertThrows(IllegalArgumentException.class, () -> new LessonScore(value));
  }
}
