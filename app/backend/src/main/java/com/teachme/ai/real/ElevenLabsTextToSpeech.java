package com.teachme.ai.real;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.teachme.ai.AiCallContext;
import com.teachme.ai.Media;
import com.teachme.ai.TextToSpeech;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * ElevenLabs の音声合成 (Text to Speech) を呼ぶ {@link TextToSpeech} (アーキテクチャ設計 4.3)。モデルは
 * `ELEVENLABS_TTS_MODEL` で決める。
 *
 * <p>話し手ごとに声の ID を選ぶ。AI 生徒は種類ごと、先輩先生は 1 つの声を使う。音声は mp3 で受け取る。空の音声は失敗とする。
 */
public final class ElevenLabsTextToSpeech implements TextToSpeech {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String PATH = "/v1/text-to-speech/";

  /** 出力の形式。mp3 の 44.1 kHz、128 kbps。 */
  private static final String OUTPUT_FORMAT = "mp3_44100_128";

  private static final String MIME_TYPE = "audio/mpeg";

  private final ElevenLabsSettings settings;
  private final ElevenLabsClient client;

  public ElevenLabsTextToSpeech(ElevenLabsSettings settings) {
    this(settings, HttpClient.newHttpClient(), ElevenLabsClient.TO_SLF4J);
  }

  /** テストが、スタブのサーバーと、ログの出し先を差し替えるための口。 */
  ElevenLabsTextToSpeech(ElevenLabsSettings settings, HttpClient http, Consumer<String> log) {
    this.settings = settings;
    this.client = new ElevenLabsClient(settings.endpoint(), settings.apiKey(), http, log);
  }

  @Override
  public Media synthesize(AiCallContext context, Voice voice, String text) {
    String path =
        PATH
            + URLEncoder.encode(voiceId(voice), StandardCharsets.UTF_8)
            + "?output_format="
            + OUTPUT_FORMAT;
    HttpRequest request =
        client
            .request(path, settings.ttsTimeout())
            .header("Content-Type", "application/json")
            .header("Accept", MIME_TYPE)
            .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody(text)))
            .build();
    byte[] audio =
        client.send(
            context.kind(),
            settings.ttsModel(),
            settings.ttsTimeout(),
            request,
            ElevenLabsTextToSpeech::audio);
    return new Media(MIME_TYPE, audio);
  }

  private String voiceId(Voice voice) {
    return switch (voice) {
      case Voice.Student student -> settings.studentVoiceIds().get(student.studentType());
      case Voice.Mentor mentor -> settings.mentorVoiceId();
    };
  }

  private byte[] requestBody(String text) {
    ObjectNode body = MAPPER.createObjectNode();
    body.put("text", text);
    body.put("model_id", settings.ttsModel());
    try {
      return MAPPER.writeValueAsBytes(body);
    } catch (JsonProcessingException e) {
      // ObjectNode の書き出しは失敗しない。失敗したら、実装の誤り
      throw new IllegalStateException("リクエストの JSON を書けませんでした", e);
    }
  }

  private static byte[] audio(byte[] body) throws IOException {
    if (body.length == 0) {
      throw new IOException("音声が空です");
    }
    return body;
  }
}
