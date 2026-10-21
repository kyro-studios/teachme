package com.teachme.ai.real;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.teachme.domain.StudentType;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ElevenLabsSettingsTest {

  private static final Map<StudentType, String> VOICES =
      Map.of(
          StudentType.INTUITIVE, "a",
          StudentType.DILIGENT, "b",
          StudentType.LOGICAL, "c");

  @Test
  void 生徒の種類のどれかの声が無いと_設定を作れない() {
    Map<StudentType, String> voices = new HashMap<>(VOICES);
    voices.remove(StudentType.LOGICAL);

    assertThrows(
        IllegalArgumentException.class,
        () -> ElevenLabsSettings.of("key", "stt", "tts", voices, "mentor"));
  }

  @Test
  void 先輩先生の声が無いと_設定を作れない() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ElevenLabsSettings.of("key", "stt", "tts", VOICES, " "));
  }

  @Test
  void 文字列に変換しても_API_キーを出さない() {
    assertFalse(
        ElevenLabsSettings.of("secret-key", "stt", "tts", VOICES, "mentor")
            .toString()
            .contains("secret-key"));
  }
}
