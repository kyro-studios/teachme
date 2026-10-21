package com.teachme.bootstrap;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * 環境変数から読む、バックエンドの起動の設定。
 *
 * @param llm `AI_MODE=real` のときだけ、LLM の設定 (fake のときは、秘密情報を読まない)
 */
public record AppConfig(
    int port, Optional<Path> staticDir, AiMode aiMode, Optional<LlmConfig> llm) {

  public enum AiMode {
    REAL,
    FAKE
  }

  private static final int DEFAULT_PORT = 8080;

  /**
   * `ENV=production` のときは `AI_MODE` を `real` に固定し、`fake` を指定すると起動に失敗する。`real` のときは、LLM
   * の必須の環境変数を検査する。
   *
   * @throws IllegalStateException `real` で LLM の環境変数が無いとき、または設定の誤り
   */
  public static AppConfig fromEnv(Map<String, String> env) {
    boolean production = "production".equals(env.get("ENV"));
    String aiModeValue = env.get("AI_MODE");
    AiMode aiMode;
    if (aiModeValue == null || aiModeValue.isBlank()) {
      aiMode = production ? AiMode.REAL : AiMode.FAKE;
    } else {
      aiMode =
          switch (aiModeValue) {
            case "real" -> AiMode.REAL;
            case "fake" -> AiMode.FAKE;
            default -> throw new IllegalStateException("AI_MODE は real か fake を指定してください");
          };
    }
    if (production && aiMode == AiMode.FAKE) {
      throw new IllegalStateException("本番では AI_MODE=fake を指定できません");
    }
    Optional<LlmConfig> llm =
        aiMode == AiMode.REAL ? Optional.of(LlmConfig.fromEnv(env)) : Optional.empty();

    String portValue = env.get("PORT");
    int port =
        portValue == null || portValue.isBlank() ? DEFAULT_PORT : Integer.parseInt(portValue);

    String staticDirValue = env.get("STATIC_DIR");
    Optional<Path> staticDir =
        staticDirValue == null || staticDirValue.isBlank()
            ? Optional.empty()
            : Optional.of(Path.of(staticDirValue));

    return new AppConfig(port, staticDir, aiMode, llm);
  }
}
