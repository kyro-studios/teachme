package com.teachme.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.teachme.bootstrap.AppConfig.AiMode;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppConfigTest {

  private static final Map<String, String> LLM =
      Map.of(
          "LLM_API_KEY", "test-key",
          "LLM_MODEL_ORGANIZE", "organize-model",
          "LLM_MODEL_DIALOGUE", "dialogue-model");

  /** 指定の環境変数に、LLM の設定を足す。 */
  private static Map<String, String> withLlm(Map<String, String> base) {
    Map<String, String> env = new HashMap<>(base);
    env.putAll(LLM);
    return env;
  }

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
    assertEquals(AiMode.REAL, AppConfig.fromEnv(withLlm(Map.of("ENV", "production"))).aiMode());
  }

  @Test
  void 本番で_fake_を指定すると失敗する() {
    assertThrows(
        IllegalStateException.class,
        () -> AppConfig.fromEnv(Map.of("ENV", "production", "AI_MODE", "fake")));
  }

  @Test
  void real_のときは_LLM_の設定を読む() {
    AppConfig config = AppConfig.fromEnv(withLlm(Map.of("AI_MODE", "real")));

    assertEquals(AiMode.REAL, config.aiMode());
    assertEquals("organize-model", config.llm().orElseThrow().organizeModel());
    assertEquals("dialogue-model", config.llm().orElseThrow().dialogueModel());
  }

  @Test
  void real_で_LLM_の環境変数が無いと_起動に失敗する() {
    IllegalStateException error =
        assertThrows(
            IllegalStateException.class, () -> AppConfig.fromEnv(Map.of("AI_MODE", "real")));

    assertTrue(error.getMessage().contains("LLM_API_KEY"));
  }

  @Test
  void fake_のときは_LLM_の環境変数を読まない() {
    AppConfig config = AppConfig.fromEnv(withLlm(Map.of("AI_MODE", "fake")));

    assertTrue(config.llm().isEmpty());
  }
}
