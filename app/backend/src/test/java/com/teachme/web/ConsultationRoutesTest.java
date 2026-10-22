package com.teachme.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.teachme.ai.AiCallKind;
import com.teachme.ai.ScriptedResponse;
import com.teachme.ai.fake.FakeAi;
import com.teachme.application.ConsultationService;
import com.teachme.application.DialogueService;
import com.teachme.application.SettingsService;
import com.teachme.application.SettingsService.SettingsCommand;
import com.teachme.domain.DialogueLimits;
import com.teachme.domain.OrganizedProblem;
import com.teachme.domain.StudentType;
import com.teachme.domain.Subject;
import com.teachme.domain.UnitName;
import com.teachme.persistence.MaterialRepository.NewImage;
import com.teachme.persistence.Repositories;
import com.teachme.testsupport.Fixtures;
import com.teachme.testsupport.MutableClock;
import io.javalin.Javalin;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 先輩先生への相談の API (`/api/consultations`) の統合テスト。DB は Testcontainers の PostgreSQL を使い、外部 AI はフェイクにする。
 */
class ConsultationRoutesTest {
  private static final String USER = "11111111-1111-1111-1111-111111111111";
  private static final String OTHER = "22222222-2222-2222-2222-222222222222";
  private static final String BOUNDARY = "teachme-consultation-boundary";
  private static final Instant START = Instant.parse("2026-10-21T01:00:00Z");
  private static final String PROBLEM = "y = x^2 - 4x + 3 の頂点を求めなさい";
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};
  private static final byte[] AUDIO = {1, 2, 3};
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  private FakeAi fakeAi;
  private UUID materialId;
  private Javalin app;

  @BeforeEach
  void setUp() {
    DataSource dataSource = Fixtures.freshDataSource();
    Repositories repositories = new Repositories(dataSource);
    MutableClock clock = new MutableClock(START, Fixtures.JAPAN);
    SettingsService settings = new SettingsService(repositories.settings(), clock);
    settings.save(UUID.fromString(USER), command("二次関数"));
    settings.save(UUID.fromString(OTHER), command("三角比"));
    materialId =
        organizedMaterial(
            repositories, settings.find(UUID.fromString(USER)).orElseThrow().unitId(), clock);

    fakeAi = new FakeAi();
    DialogueService dialogue =
        new DialogueService(settings, repositories.dialogues(), fakeAi, fakeAi, clock);
    ConsultationService consultations =
        new ConsultationService(
            settings,
            dialogue,
            repositories.dialogues(),
            repositories.materials(),
            fakeAi,
            id -> DialogueLimits.consultation(),
            clock);
    app = WebApp.create(Optional.empty(), List.of(new ConsultationRoutes(consultations)));
    app.start(0);
  }

  @AfterEach
  void tearDown() {
    app.stop();
  }

  private static SettingsCommand command(String unitName) {
    return new SettingsCommand(
        StudentType.LOGICAL, Subject.MATH, UnitName.parse(unitName).orElseThrow(), 3, true);
  }

  private static UUID organizedMaterial(
      Repositories repositories, UUID unitId, MutableClock clock) {
    UUID id = UUID.randomUUID();
    repositories
        .materials()
        .insertOrganizing(id, unitId, List.of(new NewImage("image/png", PNG)), clock.instant());
    OrganizedProblem problem =
        OrganizedProblem.parse(
                PROBLEM,
                List.of(),
                "頂点は (2, -1)",
                List.of("平方完成の形を作る"),
                "y = (x - 2)^2 と書いて手が止まっている",
                List.of("平方完成をする", "頂点を読む", "符号に注意する"),
                1)
            .orElseThrow();
    repositories.materials().markOrganized(id, problem);
    return id;
  }

  @Test
  void 開始の応答は契約どおりで_模範解答と解説を含まない() {
    HttpResponse<byte[]> response = start(USER);

    assertEquals(201, response.statusCode());
    JsonNode body = json(response);
    assertEquals(15, body.get("maxRounds").asInt());
    assertEquals(PROBLEM, body.get("problemText").asText());
    assertTrue(body.has("consultationId"));
    assertTrue(body.has("serverNow"));
    assertTrue(body.has("deadlineAt"));
    assertFalse(body.has("student"));
    assertFalse(body.has("opening"));
    assertFalse(body.has("modelAnswer"));
    assertFalse(body.has("explanationSteps"));
    assertFalse(body.has("teachingPoints"));
  }

  @Test
  void 発話と応答で_先輩先生の字幕とボードへの書き込みを返し_音声を返す() {
    String consultationId = consultationIdOf(start(USER));
    String utteranceId = utteranceIdOf(utter(USER, consultationId));
    fakeAi.script(
        UUID.fromString(USER),
        AiCallKind.MENTOR_REPLY,
        List.of(
            new ScriptedResponse(
                true,
                Map.<String, Object>of(
                    "text",
                    "判別式を使います",
                    "boardWrites",
                    List.of(
                        Map.of("kind", "text", "text", "D = b^2 - 4ac"),
                        Map.of(
                            "kind",
                            "highlight",
                            "region",
                            Map.of("x", 0.1, "y", 0.2, "width", 0.3, "height", 0.1)))))),
        false);

    HttpResponse<byte[]> reply = reply(USER, consultationId, utteranceId, PNG);

    assertEquals(200, reply.statusCode());
    JsonNode body = json(reply);
    assertEquals("mentor", body.get("turn").get("speaker").asText());
    assertEquals("判別式を使います", body.get("turn").get("text").asText());
    assertFalse(body.get("turn").has("expression"));
    assertEquals(1, body.get("roundCount").asInt());
    assertFalse(body.get("limitReached").asBoolean());
    JsonNode writes = body.get("boardWrites");
    assertEquals("text", writes.get(0).get("kind").asText());
    assertEquals("D = b^2 - 4ac", writes.get(0).get("text").asText());
    assertFalse(writes.get(0).has("region"));
    assertEquals("highlight", writes.get(1).get("kind").asText());
    assertEquals(0.1, writes.get(1).get("region").get("x").asDouble());
    assertFalse(writes.get(1).has("text"));

    String turnId = body.get("turn").get("turnId").asText();
    HttpResponse<byte[]> audio =
        get(USER, "/api/consultations/" + consultationId + "/turns/" + turnId + "/audio");
    assertEquals(200, audio.statusCode());
    assertTrue(audio.headers().firstValue("Content-Type").orElse("").startsWith("audio/mpeg"));
    assertTrue(audio.body().length > 0);
  }

  @Test
  void 先輩先生の応答が失敗したら_502_で_往復は増えず_話し直せる() {
    String consultationId = consultationIdOf(start(USER));
    String utteranceId = utteranceIdOf(utter(USER, consultationId));
    fakeAi.script(
        UUID.fromString(USER),
        AiCallKind.MENTOR_REPLY,
        List.of(new ScriptedResponse(false, null)),
        false);

    HttpResponse<byte[]> failed = reply(USER, consultationId, utteranceId, PNG);

    assertEquals(502, failed.statusCode());
    assertEquals("mentor_reply_failed", json(failed).get("error").get("code").asText());
    // 話し直しは、新しい発話で行う
    String again = utteranceIdOf(utter(USER, consultationId));
    assertEquals(200, reply(USER, consultationId, again, PNG).statusCode());
  }

  @Test
  void 中断は_何度呼んでも_204_で_以後の発話は_404() {
    String consultationId = consultationIdOf(start(USER));

    assertEquals(204, abandon(USER, consultationId).statusCode());
    assertEquals(204, abandon(USER, consultationId).statusCode());
    assertEquals(404, utter(USER, consultationId).statusCode());
  }

  @Test
  void ほかの利用者の相談には_404_を返す() {
    String consultationId = consultationIdOf(start(USER));

    HttpResponse<byte[]> utterance = utter(OTHER, consultationId);

    assertEquals(404, utterance.statusCode());
    assertEquals("not_found", json(utterance).get("error").get("code").asText());
  }

  private HttpResponse<byte[]> start(String userId) {
    byte[] body = ("{\"materialId\":\"" + materialId + "\"}").getBytes(StandardCharsets.UTF_8);
    return send("POST", "/api/consultations", userId, "application/json", body);
  }

  private HttpResponse<byte[]> utter(String userId, String consultationId) {
    return send(
        "POST",
        "/api/consultations/" + consultationId + "/utterances",
        userId,
        multipartContentType(),
        multipart("audio", "audio/webm", AUDIO, Map.of()));
  }

  private HttpResponse<byte[]> reply(
      String userId, String consultationId, String utteranceId, byte[] board) {
    return send(
        "POST",
        "/api/consultations/" + consultationId + "/utterances/" + utteranceId + "/reply",
        userId,
        multipartContentType(),
        multipart("boardImage", "image/png", board, Map.of()));
  }

  private HttpResponse<byte[]> abandon(String userId, String consultationId) {
    return send("POST", "/api/consultations/" + consultationId + "/abandon", userId, null, null);
  }

  private HttpResponse<byte[]> get(String userId, String path) {
    return send("GET", path, userId, null, null);
  }

  private static String consultationIdOf(HttpResponse<byte[]> start) {
    return json(start).get("consultationId").asText();
  }

  private static String utteranceIdOf(HttpResponse<byte[]> utterance) {
    return json(utterance).get("utteranceId").asText();
  }

  private HttpResponse<byte[]> send(
      String method, String path, String userId, String contentType, byte[] body) {
    try {
      HttpRequest.BodyPublisher publisher =
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofByteArray(body);
      HttpRequest.Builder request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path))
              .method(method, publisher);
      request.header("Cookie", "teachme_uid=" + userId);
      if (contentType != null) {
        request.header("Content-Type", contentType);
      }
      return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static String multipartContentType() {
    return "multipart/form-data; boundary=" + BOUNDARY;
  }

  /** 1 つのファイルの欄と、文字の欄を持つ multipart の本文。 */
  private static byte[] multipart(
      String name, String contentType, byte[] content, Map<String, String> fields) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (Map.Entry<String, String> field : fields.entrySet()) {
      write(
          out,
          "--"
              + BOUNDARY
              + "\r\n"
              + "Content-Disposition: form-data; name=\""
              + field.getKey()
              + "\"\r\n\r\n"
              + field.getValue()
              + "\r\n");
    }
    write(
        out,
        "--"
            + BOUNDARY
            + "\r\n"
            + "Content-Disposition: form-data; name=\""
            + name
            + "\"; filename=\"file\"\r\n"
            + "Content-Type: "
            + contentType
            + "\r\n\r\n");
    write(out, content);
    write(out, "\r\n--" + BOUNDARY + "--\r\n");
    return out.toByteArray();
  }

  private static void write(ByteArrayOutputStream out, String text) {
    write(out, text.getBytes(StandardCharsets.UTF_8));
  }

  private static void write(ByteArrayOutputStream out, byte[] bytes) {
    out.writeBytes(bytes);
  }

  private static JsonNode json(HttpResponse<byte[]> response) {
    try {
      return Json.mapper().readTree(response.body());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
