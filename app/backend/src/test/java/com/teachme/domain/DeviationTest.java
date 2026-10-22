package com.teachme.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DeviationTest {

  @Test
  void 初期偏差値に理解度の_10_分の_1_を足し_小数第_1_位で表す() {
    assertEquals(new BigDecimal("69.0"), Deviation.of(StudentType.LOGICAL, 90));
    assertEquals(new BigDecimal("45.0"), Deviation.of(StudentType.INTUITIVE, 0));
    assertEquals(new BigDecimal("52.5"), Deviation.of(StudentType.DILIGENT, 5));
    assertEquals(new BigDecimal("70.0"), Deviation.of(StudentType.LOGICAL, 100));
  }

  @Test
  void 理解度が範囲の外なら拒否する() {
    assertThrows(IllegalArgumentException.class, () -> Deviation.of(StudentType.LOGICAL, -1));
    assertThrows(IllegalArgumentException.class, () -> Deviation.of(StudentType.LOGICAL, 101));
  }
}
