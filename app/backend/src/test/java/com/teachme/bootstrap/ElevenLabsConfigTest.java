package com.teachme.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.teachme.domain.StudentType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ElevenLabsConfigTest {

  private static final Map<String, String> ALL =
      Map.of(
          "ELEVENLABS_API_KEY", "secret-key",
          "ELEVENLABS_VOICE_IDS",
              "intuitive=voice-a,diligent=voice-b,logical=voice-c,mentor=voice-d",
          "ELEVENLABS_STT_MODEL", "stt-model",
          "ELEVENLABS_TTS_MODEL", "tts-model");

  @Test
  void 四つの環境変数を読み_声の_ID_を_話し手ごとに分ける() {
    ElevenLabsConfig config = ElevenLabsConfig.fromEnv(ALL);

    assertEquals("secret-key", config.apiKey());
    assertEquals("stt-model", config.sttModel());
    assertEquals("tts-model", config.ttsModel());
    assertEquals("voice-a", config.studentVoiceIds().get(StudentType.INTUITIVE));
    assertEquals("voice-b", config.studentVoiceIds().get(StudentType.DILIGENT));
    assertEquals("voice-c", config.studentVoiceIds().get(StudentType.LOGICAL));
    assertEquals("voice-d", config.mentorVoiceId());
  }

  @Test
  void 指定が無いと_起動に失敗し_名前を示す() {
    for (String name :
        List.of(
            "ELEVENLABS_API_KEY",
            "ELEVENLABS_VOICE_IDS",
            "ELEVENLABS_STT_MODEL",
            "ELEVENLABS_TTS_MODEL")) {
      Map<String, String> env = new HashMap<>(ALL);
      env.remove(name);
      IllegalStateException error =
          assertThrows(IllegalStateException.class, () -> ElevenLabsConfig.fromEnv(env));
      assertTrue(error.getMessage().contains(name), name);
    }
  }

  @Test
  void 空白だけの値は_指定が無いとみなす() {
    Map<String, String> env = new HashMap<>(ALL);
    env.put("ELEVENLABS_TTS_MODEL", "  ");

    assertThrows(IllegalStateException.class, () -> ElevenLabsConfig.fromEnv(env));
  }

  @Test
  void 声の指定に_生徒の種類か_mentor_が足りないと_起動に失敗する() {
    for (String voices :
        List.of("intuitive=a,diligent=b,mentor=d", "intuitive=a,diligent=b,logical=c")) {
      Map<String, String> env = new HashMap<>(ALL);
      env.put("ELEVENLABS_VOICE_IDS", voices);

      IllegalStateException error =
          assertThrows(IllegalStateException.class, () -> ElevenLabsConfig.fromEnv(env));
      assertTrue(error.getMessage().contains("ELEVENLABS_VOICE_IDS"), voices);
    }
  }

  @Test
  void 声の指定の形が違うと_起動に失敗する() {
    for (String voices :
        List.of(
            "intuitive",
            "intuitive=",
            "intuitive=a,diligent=b,logical=c,mentor=d,unknown=e",
            "intuitive=a,intuitive=b,diligent=c,logical=d,mentor=e",
            "intuitive=a,diligent=b,logical=c,mentor=")) {
      Map<String, String> env = new HashMap<>(ALL);
      env.put("ELEVENLABS_VOICE_IDS", voices);

      assertThrows(IllegalStateException.class, () -> ElevenLabsConfig.fromEnv(env), voices);
    }
  }

  @Test
  void 文字列に変換しても_API_キーを出さない() {
    assertFalse(ElevenLabsConfig.fromEnv(ALL).toString().contains("secret-key"));
  }
}
