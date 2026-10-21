package com.teachme.ai.real;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teachme.ai.AiCallContext;
import com.teachme.ai.Media;
import com.teachme.ai.SpeechToText;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * ElevenLabs の音声認識 (Speech to Text) を呼ぶ {@link SpeechToText} (アーキテクチャ設計 4.3)。
 *
 * <p>録音を multipart/form-data で送り、応答の `text` を認識結果とする。無言や雑音のみのときは、空の文章を返す。応答に `text` が無いときは失敗とする。
 */
public final class ElevenLabsSpeechToText implements SpeechToText {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String PATH = "/v1/speech-to-text";

  private final ElevenLabsSettings settings;
  private final ElevenLabsClient client;

  public ElevenLabsSpeechToText(ElevenLabsSettings settings) {
    this(settings, HttpClient.newHttpClient(), ElevenLabsClient.TO_SLF4J);
  }

  /** テストが、スタブのサーバーと、ログの出し先を差し替えるための口。 */
  ElevenLabsSpeechToText(ElevenLabsSettings settings, HttpClient http, Consumer<String> log) {
    this.settings = settings;
    this.client = new ElevenLabsClient(settings.endpoint(), settings.apiKey(), http, log);
  }

  @Override
  public String transcribe(AiCallContext context, Media recording) {
    String boundary = "teachme-" + UUID.randomUUID();
    HttpRequest request =
        client
            .request(PATH, settings.sttTimeout())
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(
                HttpRequest.BodyPublishers.ofByteArray(
                    multipart(boundary, settings.sttModel(), recording)))
            .build();
    return client.send(
        context.kind(),
        settings.sttModel(),
        settings.sttTimeout(),
        request,
        ElevenLabsSpeechToText::transcript);
  }

  private static String transcript(byte[] body) throws IOException {
    JsonNode text = MAPPER.readTree(body).get("text");
    if (text == null || !text.isTextual()) {
      throw new IOException("認識結果の text がありません");
    }
    return text.asText();
  }

  /** モデルの指定 (`model_id`) と録音 (`file`) を、multipart/form-data の 1 つの本文にする。 */
  static byte[] multipart(String boundary, String model, Media recording) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    writeText(
        out,
        "--"
            + boundary
            + "\r\n"
            + "Content-Disposition: form-data; name=\"model_id\"\r\n"
            + "\r\n"
            + model
            + "\r\n");
    writeText(
        out,
        "--"
            + boundary
            + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\""
            + fileName(recording.mimeType())
            + "\"\r\n"
            + "Content-Type: "
            + recording.mimeType()
            + "\r\n"
            + "\r\n");
    out.writeBytes(recording.bytes());
    writeText(out, "\r\n--" + boundary + "--\r\n");
    return out.toByteArray();
  }

  private static void writeText(ByteArrayOutputStream out, String text) {
    out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
  }

  /** `audio/webm;codecs=opus` のような形式から、拡張子を付けたファイル名を作る。 */
  private static String fileName(String mimeType) {
    String subtype = mimeType.substring(mimeType.indexOf('/') + 1).split(";", 2)[0].trim();
    return "recording." + ("mpeg".equals(subtype) ? "mp3" : subtype);
  }
}
