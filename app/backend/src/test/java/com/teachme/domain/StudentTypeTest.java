package com.teachme.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class StudentTypeTest {

  @Test
  void 種類は_3_つで_表示名_初期偏差値_目標偏差値が仕様書の値と一致する() {
    assertEquals(
        List.of(
            List.of("intuitive", "直感・感覚派", 45, 50),
            List.of("diligent", "真面目な暗記派", 52, 58),
            List.of("logical", "論理・ツッコミ派", 60, 65)),
        List.of(StudentType.values()).stream()
            .map(
                type ->
                    List.<Object>of(
                        type.id(),
                        type.displayName(),
                        type.initialDeviation(),
                        type.targetDeviation()))
            .toList());
  }

  @Test
  void 識別子から種類を求める_知らない識別子は空() {
    assertEquals(StudentType.DILIGENT, StudentType.fromId("diligent").orElseThrow());
    assertTrue(StudentType.fromId("unknown").isEmpty());
    assertTrue(StudentType.fromId(null).isEmpty());
  }
}
