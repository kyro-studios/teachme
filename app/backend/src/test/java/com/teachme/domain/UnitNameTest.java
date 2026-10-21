package com.teachme.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnitNameTest {

  @Test
  void 前後の空白を除いた名前を作る() {
    assertEquals("二次関数", UnitName.parse("  二次関数　").orElseThrow().value());
  }

  @Test
  void 空と空白だけは作れない() {
    assertTrue(UnitName.parse("").isEmpty());
    assertTrue(UnitName.parse(" 　 ").isEmpty());
    assertTrue(UnitName.parse(null).isEmpty());
  }

  @Test
  void 文字数_30_までは作れて_31_は作れない() {
    String thirty = "あ".repeat(30);

    assertEquals(thirty, UnitName.parse(thirty).orElseThrow().value());
    assertTrue(UnitName.parse("あ".repeat(31)).isEmpty());
  }

  @Test
  void 文字数は_前後の空白を除いて数える() {
    String thirty = "あ".repeat(30);

    assertEquals(thirty, UnitName.parse(" " + thirty + " ").orElseThrow().value());
  }
}
