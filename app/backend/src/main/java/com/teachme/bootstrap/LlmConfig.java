package com.teachme.bootstrap;

import java.util.Map;

/**
 * 環境変数から読む、LLM の設定。`AI_MODE=real` のときだけ読み、すべて必須 (docs/architecture.md の 2.2 と、機能のアーキテクチャ設計 2.2)。
 *
 * @param apiKey API キー。文字列に変換したときは隠す
 * @param organizeModel `LLM_MODEL_ORGANIZE` の値
 * @param dialogueModel `LLM_MODEL_DIALOGUE` の値
 */
public record LlmConfig(String apiKey, String organizeModel, String dialogueModel) {

  /**
   * @throws IllegalStateException 指定が無いとき (起動に失敗する)
   */
  public static LlmConfig fromEnv(Map<String, String> env) {
    return new LlmConfig(
        required(env, "LLM_API_KEY"),
        required(env, "LLM_MODEL_ORGANIZE"),
        required(env, "LLM_MODEL_DIALOGUE"));
  }

  /** API キーを隠す。 */
  @Override
  public String toString() {
    return "LlmConfig[apiKey=***, organizeModel="
        + organizeModel
        + ", dialogueModel="
        + dialogueModel
        + "]";
  }

  private static String required(Map<String, String> env, String name) {
    String value = env.get(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " を指定してください");
    }
    return value;
  }
}
