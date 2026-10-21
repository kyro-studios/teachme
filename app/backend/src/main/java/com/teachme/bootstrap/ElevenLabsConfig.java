package com.teachme.bootstrap;

import com.teachme.domain.StudentType;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * 環境変数から読む、ElevenLabs の設定。`AI_MODE=real` のときだけ読み、すべて必須 (docs/architecture.md の 2.2 と、機能のアーキテクチャ設計
 * 2.2)。
 *
 * <p>`ELEVENLABS_VOICE_IDS` は、`intuitive=声の ID,diligent=声の ID,logical=声の ID,mentor=声の ID`
 * の形で書く。キーは、生徒の種類の識別子と、先輩先生の `mentor`。4 つすべてが必要。
 *
 * @param apiKey API キー。文字列に変換したときは隠す
 * @param sttModel `ELEVENLABS_STT_MODEL` の値
 * @param ttsModel `ELEVENLABS_TTS_MODEL` の値
 * @param studentVoiceIds AI 生徒の種類ごとの声の ID
 * @param mentorVoiceId 先輩先生の声の ID
 */
public record ElevenLabsConfig(
    String apiKey,
    String sttModel,
    String ttsModel,
    Map<StudentType, String> studentVoiceIds,
    String mentorVoiceId) {

  /** 先輩先生の声の、`ELEVENLABS_VOICE_IDS` でのキー。 */
  static final String MENTOR_KEY = "mentor";

  /**
   * @throws IllegalStateException 指定が無いとき、声の指定の形が違うとき (起動に失敗する)
   */
  public static ElevenLabsConfig fromEnv(Map<String, String> env) {
    String apiKey = required(env, "ELEVENLABS_API_KEY");
    String voiceIds = required(env, "ELEVENLABS_VOICE_IDS");
    String sttModel = required(env, "ELEVENLABS_STT_MODEL");
    String ttsModel = required(env, "ELEVENLABS_TTS_MODEL");
    Map<String, String> voices = parseVoiceIds(voiceIds);
    Map<StudentType, String> studentVoiceIds = new EnumMap<>(StudentType.class);
    for (StudentType type : StudentType.values()) {
      studentVoiceIds.put(type, voiceOf(voices, type.id()));
    }
    return new ElevenLabsConfig(
        apiKey, sttModel, ttsModel, studentVoiceIds, voiceOf(voices, MENTOR_KEY));
  }

  /** API キーを隠す。 */
  @Override
  public String toString() {
    return "ElevenLabsConfig[apiKey=***, sttModel="
        + sttModel
        + ", ttsModel="
        + ttsModel
        + ", studentVoiceIds="
        + studentVoiceIds
        + ", mentorVoiceId="
        + mentorVoiceId
        + "]";
  }

  /** `key=value,key=value` を読む。キーは、生徒の種類の識別子と `mentor` だけを受け付ける。 */
  private static Map<String, String> parseVoiceIds(String value) {
    Map<String, String> voices = new HashMap<>();
    for (String pair : value.split(",")) {
      int equals = pair.indexOf('=');
      if (equals <= 0 || equals == pair.length() - 1) {
        throw new IllegalStateException("ELEVENLABS_VOICE_IDS は key=value の形で指定してください");
      }
      String key = pair.substring(0, equals).trim();
      String id = pair.substring(equals + 1).trim();
      if (!isKnownKey(key) || id.isEmpty() || voices.put(key, id) != null) {
        throw new IllegalStateException("ELEVENLABS_VOICE_IDS の指定が正しくありません: " + key);
      }
    }
    return voices;
  }

  private static boolean isKnownKey(String key) {
    return key.equals(MENTOR_KEY) || StudentType.fromId(key).isPresent();
  }

  private static String voiceOf(Map<String, String> voices, String key) {
    String id = voices.get(key);
    if (id == null) {
      throw new IllegalStateException("ELEVENLABS_VOICE_IDS に " + key + " の声を指定してください");
    }
    return id;
  }

  private static String required(Map<String, String> env, String name) {
    String value = env.get(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " を指定してください");
    }
    return value;
  }
}
