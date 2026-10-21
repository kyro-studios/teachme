package com.teachme.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.teachme.bootstrap.AppConfig.AiMode;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppConfigTest {
  @Test
  void 指定が無ければ_8080_と_fake_で静的ファイルなしになる() {
    AppConfig config = AppConfig.fromEnv(Map.of());

    assertEquals(8080, config.port());
    assertEquals(AiMode.FAKE, config.aiMode());
    assertTrue(config.staticDir().isEmpty());
  }

  @Test
  void 環境変数の値を読む() {
    AppConfig config =
        AppConfig.fromEnv(Map.of("PORT", "4000", "AI_MODE", "fake", "STATIC_DIR", "app/dist"));

    assertEquals(4000, config.port());
    assertEquals(Path.of("app/dist"), config.staticDir().orElseThrow());
  }

  @Test
  void 本番では_real_に固定される() {
    assertEquals(AiMode.REAL, AppConfig.fromEnv(Map.of("ENV", "production")).aiMode());
  }

  @Test
  void 本番で_fake_を指定すると失敗する() {
    assertThrows(
        IllegalStateException.class,
        () -> AppConfig.fromEnv(Map.of("ENV", "production", "AI_MODE", "fake")));
  }
}
