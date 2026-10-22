package com.teachme.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DegreesTest {

  @Test
  void 初めの度合いは_3_件とも_0() {
    assertEquals(List.of(0, 0, 0), Degrees.initial().values());
  }

  @Test
  void 取り込むときは_指導のポイントごとに_大きいほうの値を残す() {
    Degrees current = new Degrees(List.of(1, 2, 0));
    Degrees evaluated = new Degrees(List.of(0, 1, 2));

    assertEquals(List.of(1, 2, 2), current.mergeMax(evaluated).values());
    assertEquals(List.of(1, 2, 2), evaluated.mergeMax(current).values());
  }

  @Test
  void 度合いは_3_件でなければ_IllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> new Degrees(List.of(0, 0)));
    assertThrows(IllegalArgumentException.class, () -> new Degrees(List.of(0, 0, 0, 0)));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 3})
  void 度合いの各値は_0_から_2_の外なら_IllegalArgumentException(int value) {
    assertThrows(IllegalArgumentException.class, () -> new Degrees(Arrays.asList(0, value, 0)));
  }

  @Test
  void 度合いは_作った後に変えられない() {
    List<Integer> source = new java.util.ArrayList<>(List.of(1, 1, 1));
    Degrees degrees = new Degrees(source);
    source.set(0, 2);

    assertEquals(List.of(1, 1, 1), degrees.values());
  }
}
