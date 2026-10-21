package com.teachme.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.teachme.application.DialogueService.Recording;
import com.teachme.application.ExternalFailureException;
import com.teachme.application.NotFoundException;
import io.javalin.Javalin;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DialogueRequestsTest {
  private static final String BOUNDARY = "teachme-test-boundary";

  private static final Routes ROUTES =
      app -> {
        app.post(
            "/api/recording",
            ctx -> {
              Recording recording = DialogueRequests.readRecording(ctx);
              ctx.result(recording.mimeType() + " " + recording.bytes().length);
            });
        app.post(
            "/api/not-found",
            ctx -> {
              throw new NotFoundException();
            });
        app.post(
            "/api/tts-failed",
            ctx -> {
              throw new ExternalFailureException("tts_failed", "音声を作れませんでした");
            });
      };

  @Test
  void 形式が申告されなければ_invalid_audio_になる() throws Exception {
    HttpResponse<String> response = post("/api/recording", new byte[] {1}, null);

    assertEquals(400, response.statusCode());
    assertEquals("invalid_audio", json(response).get("error").get("code").asText());
  }

  @Test
  void 受け付けない形式の録音は_invalid_audio_になる() throws Exception {
    for (String contentType : List.of("application/octet-stream", "image/png", "audio/ogg")) {
      HttpResponse<String> response = post("/api/recording", new byte[] {1}, contentType);

      assertEquals(400, response.statusCode(), contentType);
      assertEquals("invalid_audio", json(response).get("error").get("code").asText());
    }
  }

  @Test
  void 付属の部分が付いた形式は_本体で比べて受け取る() throws Exception {
    HttpResponse<String> response =
        post("/api/recording", new byte[] {1, 2}, "Audio/WebM; codecs=opus");

    assertEquals(200, response.statusCode());
    assertEquals("audio/webm 2", response.body());
  }

  @Test
  void 空の録音は_invalid_audio_になる() throws Exception {
    HttpResponse<String> response = post("/api/recording", new byte[0], "audio/webm");

    assertEquals(400, response.statusCode());
    assertEquals("invalid_audio", json(response).get("error").get("code").asText());
  }

  @Test
  void 五メガバイトを超える録音は_invalid_audio_になる() throws Exception {
    byte[] tooLarge = new byte[(int) Recording.MAX_BYTES + 1];

    HttpResponse<String> response = post("/api/recording", tooLarge, "audio/webm");

    assertEquals(400, response.statusCode());
    assertEquals("invalid_audio", json(response).get("error").get("code").asText());
  }

  @Test
  void 録音を受け取ると_形式と大きさを_そのまま渡す() throws Exception {
    HttpResponse<String> response = post("/api/recording", new byte[] {1, 2, 3}, "audio/webm");

    assertEquals(200, response.statusCode());
    assertEquals("audio/webm 3", response.body());
  }

  @Test
  void 利用者が見つからない失敗は_404_not_found_になる() throws Exception {
    HttpResponse<String> response = post("/api/not-found", new byte[0], null);

    assertEquals(404, response.statusCode());
    assertEquals("not_found", json(response).get("error").get("code").asText());
  }

  @Test
  void 外部の失敗は_502_と_code_になる() throws Exception {
    HttpResponse<String> response = post("/api/tts-failed", new byte[0], null);

    assertEquals(502, response.statusCode());
    assertEquals("tts_failed", json(response).get("error").get("code").asText());
  }

  /** 録音を multipart の `audio` として送る。`contentType` が null なら、形式を付けない。 */
  private static HttpResponse<String> post(String path, byte[] audio, String contentType)
      throws Exception {
    Javalin app = WebApp.create(Optional.empty(), List.of(ROUTES));
    app.start(0);
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path))
              .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
              .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(audio, contentType)))
              .build();
      return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    } finally {
      app.stop();
    }
  }

  private static byte[] multipart(byte[] audio, String contentType) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    String type = contentType == null ? "" : "Content-Type: " + contentType + "\r\n";
    out.write(
        part(
            "--"
                + BOUNDARY
                + "\r\n"
                + "Content-Disposition: form-data; name=\"audio\"; filename=\"utterance\"\r\n"
                + type
                + "\r\n"));
    out.write(audio);
    out.write(part("\r\n--" + BOUNDARY + "--\r\n"));
    return out.toByteArray();
  }

  private static byte[] part(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static JsonNode json(HttpResponse<String> response) throws IOException {
    return Json.mapper().readTree(response.body());
  }
}
