package com.teachme.ai;

/**
 * 外部 AI の呼び出しの種類。
 *
 * <p>`wireName` は、テスト用の API (`/api/test/ai-script` と `/api/test/ai-calls`) の `kind` と、openapi.yaml
 * の決まりに合わせた識別子である。
 */
public enum AiCallKind {
  ORGANIZE("organize"),
  STUDENT_REPLY("student_reply"),
  EVALUATION("evaluation"),
  MENTOR_REPLY("mentor_reply"),
  SUMMARY("summary"),
  MOCK_EXAM("mock_exam"),
  HOMEWORK("homework"),
  STT("stt"),
  TTS("tts");

  private final String wireName;

  AiCallKind(String wireName) {
    this.wireName = wireName;
  }

  public String wireName() {
    return wireName;
  }

  /**
   * @throws IllegalArgumentException 知らない種類のとき
   */
  public static AiCallKind fromWireName(String wireName) {
    for (AiCallKind kind : values()) {
      if (kind.wireName.equals(wireName)) {
        return kind;
      }
    }
    throw new IllegalArgumentException("知らない AI の呼び出しの種類です");
  }
}
