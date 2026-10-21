package com.teachme.bootstrap;

import com.teachme.ai.LanguageModel;
import com.teachme.ai.real.OpenAiLanguageModel;
import com.teachme.ai.real.OpenAiSettings;

/** `AI_MODE=real` のときの外部 AI の組み立て (docs/features/1-ai-student-lesson/architecture.md の 4.3)。 */
public final class RealAi {

  private RealAi() {}

  /** LLM の実装 (`ai.real`) を、本番の API の既定の設定で組み立てる。 */
  public static LanguageModel languageModel(LlmConfig config) {
    return new OpenAiLanguageModel(
        OpenAiSettings.of(config.apiKey(), config.organizeModel(), config.dialogueModel()));
  }
}
