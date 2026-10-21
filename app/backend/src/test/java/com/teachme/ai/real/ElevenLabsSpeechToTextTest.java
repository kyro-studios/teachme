package com.teachme.ai.real;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.teachme.ai.AiCallContext;
import com.teachme.ai.AiCallKind;
import com.teachme.ai.AiFailedException;
import com.teachme.ai.Media;
import com.teachme.ai.real.ElevenLabsStub.Recorded;
import com.teachme.ai.real.ElevenLabsStub.Reply;
import com.teachme.domain.StudentType;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ElevenLabsSpeechToTextTest {

  private static final AiCallContext CONTEXT =
      new AiCallContext(UUID.fromString("11111111-1111-1111-1111-111111111111"), AiCallKind.STT);
  private static final Media RECORDING =
      new Media("audio/webm", "録音の音".getBytes(StandardCharsets.UTF_8));
  private static final Duration LONG = Duration.ofSeconds(5);
  private static final Duration SHORT = Duration.ofMillis(300);

  private static final String SUCCESS_LOG =
      "ai_call kind=stt model=stt-model duration_ms=\\d+ outcome=success";

  private ElevenLabsStub stub;
  private final List<String> logs = new CopyOnWriteArrayList<>();

  @BeforeEach
  void startStub() throws IOException {
    stub = new ElevenLabsStub();
  }

  @AfterEach
  void stopStub() {
    stub.close();
  }

  private ElevenLabsSpeechToText stt(Duration timeout) {
    ElevenLabsSettings settings =
        new ElevenLabsSettings(
            stub.endpoint(),
            "test-key",
            "stt-model",
            "tts-model",
            Map.of(
                StudentType.INTUITIVE, "voice-intuitive",
                StudentType.DILIGENT, "voice-diligent",
                StudentType.LOGICAL, "voice-logical"),
            "voice-mentor",
            timeout,
            LONG);
    return new ElevenLabsSpeechToText(settings, HttpClient.newHttpClient(), logs::add);
  }

  @Test
  void 録音を送り_応答の文章を返す() {
    stub.enqueue(Reply.json("{\"language_code\":\"ja\",\"text\":\"こんにちは\"}"));

    String text = stt(LONG).transcribe(CONTEXT, RECORDING);

    assertEquals("こんにちは", text);
    Recorded request = stub.requests().getFirst();
    assertEquals("POST", request.method());
    assertEquals("/v1/speech-to-text", request.target());
    assertEquals("test-key", request.apiKey());
    assertTrue(request.contentType().startsWith("multipart/form-data; boundary="));
    assertTrue(request.bodyAsIso().contains("name=\"model_id\"\r\n\r\nstt-model\r\n"));
    assertTrue(request.bodyAsIso().contains("filename=\"recording.webm\""));
    assertTrue(request.bodyAsIso().contains("Content-Type: audio/webm\r\n\r\n"));
    assertTrue(
        request.bodyAsIso().contains(new String(RECORDING.bytes(), StandardCharsets.ISO_8859_1)));
  }

  @Test
  void 無言のときは_空の文章を返す() {
    stub.enqueue(Reply.json("{\"text\":\"\"}"));

    assertEquals("", stt(LONG).transcribe(CONTEXT, RECORDING));
  }

  @Test
  void 成功のログは_1_行で_中身と_API_キーを出さない() {
    stub.enqueue(Reply.json("{\"text\":\"こんにちは\"}"));

    stt(LONG).transcribe(CONTEXT, RECORDING);

    assertEquals(1, logs.size());
    assertTrue(logs.getFirst().matches(SUCCESS_LOG), logs.getFirst());
    assertFalse(logs.getFirst().contains("こんにちは"));
    assertFalse(logs.getFirst().contains("test-key"));
  }

  @Test
  void 応答に_text_が無いと_失敗にする() {
    stub.enqueue(Reply.json("{\"language_code\":\"ja\"}"));

    AiFailedException error =
        assertThrows(AiFailedException.class, () -> stt(LONG).transcribe(CONTEXT, RECORDING));

    assertEquals(AiCallKind.STT, error.kind());
    assertTrue(logs.getFirst().contains("reason=malformed"), logs.getFirst());
  }

  @Test
  void HTTP_のエラーは_失敗にする() {
    stub.enqueue(Reply.status(500));

    AiFailedException error =
        assertThrows(AiFailedException.class, () -> stt(LONG).transcribe(CONTEXT, RECORDING));

    assertEquals(AiCallKind.STT, error.kind());
    assertTrue(logs.getFirst().contains("reason=http_status status=500"), logs.getFirst());
  }

  @Test
  void 応答の見出しが期限を過ぎると_失敗にする() {
    stub.enqueue(new Reply(200, "{\"text\":\"遅い\"}".getBytes(StandardCharsets.UTF_8), 2000, 0));

    assertThrows(AiFailedException.class, () -> stt(SHORT).transcribe(CONTEXT, RECORDING));

    assertTrue(logs.getFirst().contains("reason=timeout"), logs.getFirst());
  }

  @Test
  void 応答の本文が期限を過ぎても_失敗にする() {
    stub.enqueue(new Reply(200, "{\"text\":\"遅い\"}".getBytes(StandardCharsets.UTF_8), 0, 2000));

    assertThrows(AiFailedException.class, () -> stt(SHORT).transcribe(CONTEXT, RECORDING));

    assertTrue(logs.getFirst().contains("reason=timeout"), logs.getFirst());
  }
}
