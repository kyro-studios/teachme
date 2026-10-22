package com.teachme.bootstrap;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.teachme.ai.LanguageModel;
import com.teachme.ai.SpeechToText;
import com.teachme.ai.TextToSpeech;
import com.teachme.ai.real.ElevenLabsSpeechToText;
import com.teachme.ai.real.ElevenLabsTextToSpeech;
import com.teachme.ai.real.OpenAiLanguageModel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RealAiTest {

  private static final ElevenLabsConfig ELEVEN =
      ElevenLabsConfig.fromEnv(
          Map.of(
              "ELEVENLABS_API_KEY", "secret-key",
              "ELEVENLABS_VOICE_IDS", "intuitive=a,diligent=b,logical=c,mentor=d",
              "ELEVENLABS_STT_MODEL", "stt-model",
              "ELEVENLABS_TTS_MODEL", "tts-model"));

  private static final LlmConfig LLM =
      LlmConfig.fromEnv(
          Map.of(
              "LLM_API_KEY", "secret-key",
              "LLM_MODEL_ORGANIZE", "organize-model",
              "LLM_MODEL_DIALOGUE", "dialogue-model"));

  @Test
  void real_の組み立ては_LLM_の実装を返す() {
    LanguageModel llm = RealAi.languageModel(LLM);

    assertInstanceOf(OpenAiLanguageModel.class, llm);
  }

  @Test
  void real_の組み立ては_音声認識と_音声合成の実装を返す() {
    SpeechToText stt = RealAi.speechToText(ELEVEN);
    TextToSpeech tts = RealAi.textToSpeech(ELEVEN);

    assertInstanceOf(ElevenLabsSpeechToText.class, stt);
    assertInstanceOf(ElevenLabsTextToSpeech.class, tts);
  }
}
