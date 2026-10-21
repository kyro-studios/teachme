package com.teachme.ai.real;

import java.net.URI;
import java.time.Duration;

/**
 * LLM (初期の実装は OpenAI の Chat Completions API) への接続の設定 (アーキテクチャ設計 4.3)。
 *
 * <p>往復ごとに呼ぶ生徒の応答、学習評価、先輩先生の応答は `dialogueModel` を使う。1 回ごとに呼ぶ整理、まとめ、模試、宿題の丸つけは `organizeModel` を使う。
 *
 * @param endpoint API の基底の URL。テストでは、スタブのサーバーを指す
 * @param apiKey API キー。文字列に変換したときは隠す
 * @param organizeModel 教材の整理、まとめ、模試、宿題の丸つけに使うモデル
 * @param dialogueModel 生徒の応答、学習評価、先輩先生の応答に使うモデル
 * @param dialogueTimeout 生徒の応答、学習評価、先輩先生の応答、まとめ、模試、宿題の丸つけの HTTP のタイムアウト
 * @param organizeTimeout 教材の整理の HTTP のタイムアウト
 */
public record OpenAiSettings(
    URI endpoint,
    String apiKey,
    String organizeModel,
    String dialogueModel,
    Duration dialogueTimeout,
    Duration organizeTimeout) {

  /** 本番の API の基底の URL。 */
  public static final URI DEFAULT_ENDPOINT = URI.create("https://api.openai.com");

  /** 教材の整理以外の HTTP のタイムアウト (アーキテクチャ設計 4.3)。 */
  public static final Duration LLM_TIMEOUT = Duration.ofSeconds(30);

  /** 教材の整理の HTTP のタイムアウト (アーキテクチャ設計 4.3)。 */
  public static final Duration ORGANIZE_TIMEOUT = Duration.ofSeconds(60);

  /** 本番の API の基底の URL と、既定のタイムアウトで設定を作る。 */
  public static OpenAiSettings of(String apiKey, String organizeModel, String dialogueModel) {
    return new OpenAiSettings(
        DEFAULT_ENDPOINT, apiKey, organizeModel, dialogueModel, LLM_TIMEOUT, ORGANIZE_TIMEOUT);
  }

  /** API キーを隠す。ログや例外の文言に、秘密情報を出さないため。 */
  @Override
  public String toString() {
    return "OpenAiSettings[endpoint="
        + endpoint
        + ", apiKey=***, organizeModel="
        + organizeModel
        + ", dialogueModel="
        + dialogueModel
        + ", dialogueTimeout="
        + dialogueTimeout
        + ", organizeTimeout="
        + organizeTimeout
        + "]";
  }
}
