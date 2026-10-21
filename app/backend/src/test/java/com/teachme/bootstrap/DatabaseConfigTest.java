package com.teachme.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DatabaseConfigTest {
  @Test
  void 環境変数の値を読む() {
    DatabaseConfig config =
        DatabaseConfig.fromEnv(
            Map.of(
                "DATABASE_URL", "jdbc:postgresql://127.0.0.1:5432/teachme",
                "DATABASE_USER", "teachme",
                "DATABASE_PASSWORD", "secret"));

    assertEquals(
        new DatabaseConfig("jdbc:postgresql://127.0.0.1:5432/teachme", "teachme", "secret"),
        config);
  }

  @Test
  void 指定が無ければ_起動に失敗する() {
    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () ->
                DatabaseConfig.fromEnv(
                    Map.of("DATABASE_USER", "teachme", "DATABASE_PASSWORD", "secret")));

    assertEquals("DATABASE_URL を指定してください", e.getMessage());
  }

  @Test
  void 空の値も_指定が無いものとして扱う() {
    assertThrows(
        IllegalStateException.class,
        () ->
            DatabaseConfig.fromEnv(
                Map.of(
                    "DATABASE_URL", "jdbc:postgresql://127.0.0.1:5432/teachme",
                    "DATABASE_USER", "teachme",
                    "DATABASE_PASSWORD", " ")));
  }
}
