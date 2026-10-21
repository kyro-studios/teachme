package com.teachme.ai.real;

import com.teachme.domain.StudentType;
import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * ElevenLabs (STT と TTS) への接続の設定 (アーキテクチャ設計 4.3)。
 *
 * <p>STT と TTS の HTTP のタイムアウトは、どちらも 20 秒とする。自動の再試行はしない。
 *
 * @param endpoint API の基底の URL。テストでは、スタブのサーバーを指す
 * @param apiKey API キー。文字列に変換したときは隠す
 * @param sttModel 音声認識のモデル名
 * @param ttsModel 音声合成のモデル名
 * @param studentVoiceIds AI 生徒の種類ごとの声の ID。3 種類すべてを持つ
 * @param mentorVoiceId 先輩先生の声の ID
 * @param sttTimeout 音声認識の HTTP のタイムアウト
 * @param ttsTimeout 音声合成の HTTP のタイムアウト
 */
public record ElevenLabsSettings(
    URI endpoint,
    String apiKey,
    String sttModel,
    String ttsModel,
    Map<StudentType, String> studentVoiceIds,
    String mentorVoiceId,
    Duration sttTimeout,
    Duration ttsTimeout) {

  /** 本番の API の基底の URL。 */
  public static final URI DEFAULT_ENDPOINT = URI.create("https://api.elevenlabs.io");

  /** 音声認識と音声合成の HTTP のタイムアウト (アーキテクチャ設計 4.3)。 */
  public static final Duration SPEECH_TIMEOUT = Duration.ofSeconds(20);

  /**
   * @throws IllegalArgumentException 声の ID が、生徒の種類の 1 つでも、先輩先生の分でも無いとき
   */
  public ElevenLabsSettings {
    Map<StudentType, String> voices = new EnumMap<>(StudentType.class);
    for (StudentType type : StudentType.values()) {
      String voice = studentVoiceIds.get(type);
      if (voice == null || voice.isBlank()) {
        throw new IllegalArgumentException("声の ID が無い生徒の種類があります: " + type.id());
      }
      voices.put(type, voice);
    }
    if (mentorVoiceId == null || mentorVoiceId.isBlank()) {
      throw new IllegalArgumentException("先輩先生の声の ID が無いです");
    }
    studentVoiceIds = Map.copyOf(voices);
  }

  /** 本番の API の基底の URL と、既定のタイムアウトで設定を作る。 */
  public static ElevenLabsSettings of(
      String apiKey,
      String sttModel,
      String ttsModel,
      Map<StudentType, String> studentVoiceIds,
      String mentorVoiceId) {
    return new ElevenLabsSettings(
        DEFAULT_ENDPOINT,
        apiKey,
        sttModel,
        ttsModel,
        studentVoiceIds,
        mentorVoiceId,
        SPEECH_TIMEOUT,
        SPEECH_TIMEOUT);
  }

  /** API キーを隠す。ログや例外の文言に、秘密情報を出さないため。 */
  @Override
  public String toString() {
    return "ElevenLabsSettings[endpoint="
        + endpoint
        + ", apiKey=***, sttModel="
        + sttModel
        + ", ttsModel="
        + ttsModel
        + ", sttTimeout="
        + sttTimeout
        + ", ttsTimeout="
        + ttsTimeout
        + "]";
  }
}
