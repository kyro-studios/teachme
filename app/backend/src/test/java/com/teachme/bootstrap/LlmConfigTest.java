package com.teachme.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.teachme.ai.LanguageModel;
import com.teachme.ai.real.OpenAiLanguageModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmConfigTest {

  private static final Map<String, String> ALL =
      Map.of(
          "LLM_API_KEY", "secret-key",
          "LLM_MODEL_ORGANIZE", "organize-model",
          "LLM_MODEL_DIALOGUE", "dialogue-model");

  @Test
  void 三つの環境変数を読む() {
    LlmConfig config = LlmConfig.fromEnv(ALL);

    assertEquals("secret-key", config.apiKey());
    assertEquals("organize-model", config.organizeModel());
    assertEquals("dialogue-model", config.dialogueModel());
  }

  @Test
  void 指定が無いと_起動に失敗し_名前を示す() {
    for (String name : List.of("LLM_API_KEY", "LLM_MODEL_ORGANIZE", "LLM_MODEL_DIALOGUE")) {
      Map<String, String> env = new HashMap<>(ALL);
      env.remove(name);
      IllegalStateException error =
          assertThrows(IllegalStateException.class, () -> LlmConfig.fromEnv(env));
      assertTrue(error.getMessage().contains(name), name);
    }
  }

  @Test
  void 空白だけの値は_指定が無いとみなす() {
    Map<String, String> env = new HashMap<>(ALL);
    env.put("LLM_MODEL_DIALOGUE", "  ");

    assertThrows(IllegalStateException.class, () -> LlmConfig.fromEnv(env));
  }

  @Test
  void 文字列に変換しても_API_キーを出さない() {
    assertFalse(LlmConfig.fromEnv(ALL).toString().contains("secret-key"));
  }

  @Test
  void real_の組み立ては_LLM_の実装を返す() {
    LanguageModel model = RealAi.languageModel(LlmConfig.fromEnv(ALL));

    assertInstanceOf(OpenAiLanguageModel.class, model);
  }
}
