package com.teachme.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.teachme.ai.fake.FakeAi;
import com.teachme.application.DialogueService;
import com.teachme.application.DialogueSweeper;
import com.teachme.application.LessonService;
import com.teachme.application.SettingsService;
import com.teachme.application.SettingsService.SettingsCommand;
import com.teachme.application.TestControlService;
import com.teachme.application.TestControlService.ScriptedAttempt;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 授業の API (`/api/lessons`) の統合テスト。DB は Testcontainers の PostgreSQL を使い、外部 AI はフェイクにする。 */
class LessonRoutesTest {
  private static final String USER = "11111111-1111-1111-1111-111111111111";
  private static final String OTHER = "22222222-2222-2222-2222-222222222222";
  private static final String BOUNDARY = "teachme-lesson-boundary";
  private static final Instant START = Instant.parse("2026-10-21T01:00:00Z");
  private static final String PROBLEM = "y = x^2 - 4x + 3 の頂点を求めなさい";
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0};
  private static final byte[] AUDIO = {1, 2, 3};
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  private MutableClock clock;
  private TestControlService testControl;
  private UUID materialId;
  private Javalin app;

  @BeforeEach
  void setUp() {
    DataSource dataSource = Fixtures.freshDataSource();
    Repositories repositories = new Repositories(dataSource);
    clock = new MutableClock(START, Fixtures.JAPAN);
    SettingsService settings = new SettingsService(repositories.settings(), clock);
    settings.save(UUID.fromString(USER), command("二次関数"));
    settings.save(UUID.fromString(OTHER), command("三角比"));
    materialId =
        organizedMaterial(
            repositories, settings.find(UUID.fromString(USER)).orElseThrow().unitId());

    FakeAi fakeAi = new FakeAi();
    testControl = new TestControlService(fakeAi);
    DialogueService dialogue =
        new DialogueService(settings, repositories.dialogues(), fakeAi, fakeAi, clock);
    LessonService lessons =
        new LessonService(
            settings,
            dialogue,
            repositories.dialogues(),
            repositories.materials(),
            repositories.todayModes(),
            repositories.lessonRecords(),
            fakeAi,
            testControl::lessonLimits,
            clock);
    app =
        WebApp.create(
            Optional.empty(),
            List.of(
                new LessonRoutes(lessons, new DialogueSweeper(repositories.dialogues(), clock)),
                new TestRoutes(testControl)));
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

  private static UUID organizedMaterial(Repositories repositories, UUID unitId) {
    UUID id = UUID.randomUUID();
    repositories
        .materials()
        .insertOrganizing(id, unitId, List.of(new NewImage("image/png", PNG)), START);
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
    HttpResponse<byte[]> response = startLesson(USER);

    assertEquals(201, response.statusCode());
    JsonNode body = json(response);
    assertEquals(20, body.get("maxRounds").asInt());
    assertEquals("logical", body.get("student").get("id").asText());
    assertEquals(PROBLEM, body.get("problemText").asText());
    assertEquals("student", body.get("opening").get("speaker").asText());
    assertEquals("troubled", body.get("opening").get("expression").asText());
    assertTrue(body.has("serverNow"));
    assertTrue(body.has("deadlineAt"));
    assertFalse(body.has("modelAnswer"));
    assertFalse(body.has("explanationSteps"));
    assertFalse(body.has("teachingPoints"));
  }

  @Test
  void 発話_1_回と応答_1_回で_LLM_を_2_回だけ呼び_音声を返す() {
    String lessonId = lessonIdOf(startLesson(USER));
    int studentReplies = aiCalls(USER, "student_reply");
    int evaluations = aiCalls(USER, "evaluation");

    String utteranceId = utteranceIdOf(utter(USER, lessonId));
    HttpResponse<byte[]> reply = reply(USER, lessonId, utteranceId, PNG, Map.of());

    assertEquals(200, reply.statusCode());
    JsonNode body = json(reply);
    assertEquals("student", body.get("turn").get("speaker").asText());
    assertEquals("troubled", body.get("turn").get("expression").asText());
    assertEquals(1, body.get("roundCount").asInt());
    assertFalse(body.get("limitReached").asBoolean());
    assertTrue(body.get("annotation").isNull());
    assertEquals(1, aiCalls(USER, "student_reply") - studentReplies);
    assertEquals(1, aiCalls(USER, "evaluation") - evaluations);

    String turnId = body.get("turn").get("turnId").asText();
    HttpResponse<byte[]> audio =
        get(USER, "/api/lessons/" + lessonId + "/turns/" + turnId + "/audio");
    assertEquals(200, audio.statusCode());
    assertTrue(audio.headers().firstValue("Content-Type").orElse("").startsWith("audio/mpeg"));
    assertTrue(audio.body().length > 0);
  }

  @Test
  void 認識できない発話は_recognized_が_false_で返し_往復に数えない() {
    testControl.scriptAi(
        UUID.fromString(USER),
        "stt",
        false,
        List.of(new ScriptedAttempt(true, Map.of("text", ""))));
    String lessonId = lessonIdOf(startLesson(USER));

    HttpResponse<byte[]> utterance = utter(USER, lessonId);

    assertEquals(200, utterance.statusCode());
    JsonNode body = json(utterance);
    assertFalse(body.get("recognized").asBoolean());
    assertFalse(body.has("utteranceId"));
  }

  @Test
  void ボードの画像が_PNG_でなければ_400_で_応答しない() {
    String lessonId = lessonIdOf(startLesson(USER));
    String utteranceId = utteranceIdOf(utter(USER, lessonId));

    HttpResponse<byte[]> reply = reply(USER, lessonId, utteranceId, JPEG, Map.of());

    assertEquals(400, reply.statusCode());
    assertEquals("invalid_board_image", json(reply).get("error").get("code").asText());
  }

  @Test
  void 指している範囲が_画像の外なら_400_で_応答しない() {
    String lessonId = lessonIdOf(startLesson(USER));
    String utteranceId = utteranceIdOf(utter(USER, lessonId));

    HttpResponse<byte[]> reply =
        reply(
            USER,
            lessonId,
            utteranceId,
            PNG,
            Map.of("pointedRegions", "[{\"x\":0.9,\"y\":0,\"width\":0.5,\"height\":0.1}]"));

    assertEquals(400, reply.statusCode());
    assertEquals("invalid_request", json(reply).get("error").get("code").asText());
  }

  @Test
  void まとめは_上限に達するまで_409_で_達したら_記録する() {
    String lessonId = lessonIdOf(startLesson(USER));
    String utteranceId = utteranceIdOf(utter(USER, lessonId));
    reply(USER, lessonId, utteranceId, PNG, Map.of());

    HttpResponse<byte[]> early = summarize(USER, lessonId, PNG);
    assertEquals(409, early.statusCode());
    assertEquals("limit_not_reached", json(early).get("error").get("code").asText());

    clock.advance(Duration.ofSeconds(598));
    HttpResponse<byte[]> summary = summarize(USER, lessonId, PNG);

    assertEquals(200, summary.statusCode());
    JsonNode body = json(summary);
    assertEquals(lessonId, body.get("recordId").asText());
    assertEquals("今日は頂点の求め方を教わりました。", body.get("summaryText").asText());
    assertEquals(1, body.get("lessonsThisWeek").asInt());
    assertEquals(0, body.get("quotes").size());
  }

  @Test
  void 中断は_何度呼んでも_204_で_字幕と授業を消す() {
    String lessonId = lessonIdOf(startLesson(USER));

    assertEquals(204, abandon(USER, lessonId).statusCode());
    assertEquals(204, abandon(USER, lessonId).statusCode());
    // 中断した授業には、発話できない
    assertEquals(404, utter(USER, lessonId).statusCode());
  }

  @Test
  void ほかの利用者の授業には_404_を返す() {
    String lessonId = lessonIdOf(startLesson(USER));

    HttpResponse<byte[]> utterance = utter(OTHER, lessonId);
    HttpResponse<byte[]> summary = summarize(OTHER, lessonId, PNG);

    assertEquals(404, utterance.statusCode());
    assertEquals(404, summary.statusCode());
    assertEquals("not_found", json(utterance).get("error").get("code").asText());
  }

  private HttpResponse<byte[]> startLesson(String userId) {
    byte[] body = ("{\"materialId\":\"" + materialId + "\"}").getBytes(StandardCharsets.UTF_8);
    return send("POST", "/api/lessons", userId, "application/json", body);
  }

  private HttpResponse<byte[]> utter(String userId, String lessonId) {
    return send(
        "POST",
        "/api/lessons/" + lessonId + "/utterances",
        userId,
        multipartContentType(),
        multipart("audio", "audio/webm", AUDIO, Map.of()));
  }

  private HttpResponse<byte[]> reply(
      String userId,
      String lessonId,
      String utteranceId,
      byte[] board,
      Map<String, String> fields) {
    return send(
        "POST",
        "/api/lessons/" + lessonId + "/utterances/" + utteranceId + "/reply",
        userId,
        multipartContentType(),
        multipart("boardImage", "image/png", board, fields));
  }

  private HttpResponse<byte[]> summarize(String userId, String lessonId, byte[] board) {
    return send(
        "POST",
        "/api/lessons/" + lessonId + "/summary",
        userId,
        multipartContentType(),
        multipart("boardImage", "image/png", board, Map.of()));
  }

  private HttpResponse<byte[]> abandon(String userId, String lessonId) {
    return send("POST", "/api/lessons/" + lessonId + "/abandon", userId, null, null);
  }

  private int aiCalls(String userId, String kind) {
    return json(get(userId, "/api/test/ai-calls")).get("counts").get(kind).asInt();
  }

  private static String lessonIdOf(HttpResponse<byte[]> start) {
    return json(start).get("lessonId").asText();
  }

  private static String utteranceIdOf(HttpResponse<byte[]> utterance) {
    return json(utterance).get("utteranceId").asText();
  }

  private HttpResponse<byte[]> get(String userId, String path) {
    return send("GET", path, userId, null, null);
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
