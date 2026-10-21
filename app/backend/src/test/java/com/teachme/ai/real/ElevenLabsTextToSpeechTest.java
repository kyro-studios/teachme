package com.teachme.ai.real;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teachme.ai.AiCallContext;
import com.teachme.ai.AiCallKind;
import com.teachme.ai.AiFailedException;
import com.teachme.ai.Media;
import com.teachme.ai.TextToSpeech.Voice;
import com.teachme.ai.real.ElevenLabsStub.Recorded;
import com.teachme.ai.real.ElevenLabsStub.Reply;
import com.teachme.domain.StudentType;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ElevenLabsTextToSpeechTest {

  private static final AiCallContext CONTEXT =
      new AiCallContext(UUID.fromString("11111111-1111-1111-1111-111111111111"), AiCallKind.TTS);
  private static final Duration LONG = Duration.ofSeconds(5);
  private static final Duration SHORT = Duration.ofMillis(300);
  private static final byte[] MP3 = {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x00, 1, 2, 3};
  private static final String SUCCESS_LOG =
      "ai_call kind=tts model=tts-model duration_ms=\\d+ outcome=success";

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

  private ElevenLabsTextToSpeech tts(Duration timeout) {
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
            LONG,
            timeout);
    return new ElevenLabsTextToSpeech(settings, HttpClient.newHttpClient(), logs::add);
  }

  private static Reply audio(byte[] bytes) {
    return new Reply(200, bytes, 0, 0);
  }

  @Test
  void 生徒の声で音声を合成し_mp3_を返す() throws IOException {
    stub.enqueue(audio(MP3));

    Media speech = tts(LONG).synthesize(CONTEXT, new Voice.Student(StudentType.INTUITIVE), "こんにちは");

    assertEquals("audio/mpeg", speech.mimeType());
    assertArrayEquals(MP3, speech.bytes());
    Recorded request = stub.requests().getFirst();
    assertEquals("POST", request.method());
    assertEquals(
        "/v1/text-to-speech/voice-intuitive?output_format=mp3_44100_128", request.target());
    assertEquals("test-key", request.apiKey());
    assertEquals("application/json", request.contentType());
    assertEquals("audio/mpeg", request.accept());
    JsonNode body = new ObjectMapper().readTree(request.body());
    assertEquals("こんにちは", body.get("text").asText());
    assertEquals("tts-model", body.get("model_id").asText());
  }

  @Test
  void 生徒の種類ごとに_別の声を使い_先輩先生は_先輩先生の声を使う() {
    stub.enqueue(audio(MP3));
    stub.enqueue(audio(MP3));
    stub.enqueue(audio(MP3));
    ElevenLabsTextToSpeech tts = tts(LONG);

    tts.synthesize(CONTEXT, new Voice.Student(StudentType.DILIGENT), "a");
    tts.synthesize(CONTEXT, new Voice.Student(StudentType.LOGICAL), "b");
    tts.synthesize(CONTEXT, new Voice.Mentor(), "c");

    List<String> targets = stub.requests().stream().map(Recorded::target).toList();
    assertTrue(targets.get(0).startsWith("/v1/text-to-speech/voice-diligent?"), targets.get(0));
    assertTrue(targets.get(1).startsWith("/v1/text-to-speech/voice-logical?"), targets.get(1));
    assertTrue(targets.get(2).startsWith("/v1/text-to-speech/voice-mentor?"), targets.get(2));
  }

  @Test
  void 成功のログは_1_行で_中身と_API_キーを出さない() {
    stub.enqueue(audio(MP3));

    tts(LONG).synthesize(CONTEXT, new Voice.Mentor(), "秘密の文章");

    assertEquals(1, logs.size());
    assertTrue(logs.getFirst().matches(SUCCESS_LOG), logs.getFirst());
    assertFalse(logs.getFirst().contains("秘密の文章"));
    assertFalse(logs.getFirst().contains("test-key"));
  }

  @Test
  void 空の音声は_失敗にする() {
    stub.enqueue(audio(new byte[0]));

    AiFailedException error =
        assertThrows(
            AiFailedException.class,
            () -> tts(LONG).synthesize(CONTEXT, new Voice.Mentor(), "こんにちは"));

    assertEquals(AiCallKind.TTS, error.kind());
    assertTrue(logs.getFirst().contains("reason=malformed"), logs.getFirst());
  }

  @Test
  void HTTP_のエラーは_失敗にする() {
    stub.enqueue(Reply.status(401));

    AiFailedException error =
        assertThrows(
            AiFailedException.class,
            () -> tts(LONG).synthesize(CONTEXT, new Voice.Mentor(), "こんにちは"));

    assertEquals(AiCallKind.TTS, error.kind());
    assertTrue(logs.getFirst().contains("reason=http_status status=401"), logs.getFirst());
  }

  @Test
  void 応答の見出しが期限を過ぎると_失敗にする() {
    stub.enqueue(new Reply(200, MP3, 2000, 0));

    assertThrows(
        AiFailedException.class, () -> tts(SHORT).synthesize(CONTEXT, new Voice.Mentor(), "こんにちは"));

    assertTrue(logs.getFirst().contains("reason=timeout"), logs.getFirst());
  }

  @Test
  void 音声の本文が期限を過ぎても_失敗にする() {
    stub.enqueue(new Reply(200, MP3, 0, 2000));

    assertThrows(
        AiFailedException.class, () -> tts(SHORT).synthesize(CONTEXT, new Voice.Mentor(), "こんにちは"));

    assertTrue(logs.getFirst().contains("reason=timeout"), logs.getFirst());
  }
}
