package com.teachme.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teachme.ai.AiCallKind;
import com.teachme.ai.ScriptedResponse;
import com.teachme.ai.fake.FakeAi;
import com.teachme.application.MockExamService;
import com.teachme.application.SettingsService;
import com.teachme.application.SettingsService.SettingsCommand;
import com.teachme.domain.StudentType;
import com.teachme.domain.Subject;
import com.teachme.domain.UnitName;
import com.teachme.persistence.Repositories;
import com.teachme.testsupport.Fixtures;
import com.teachme.testsupport.MutableClock;
import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MockExamRoutesTest {
  private static final String USER = "11111111-1111-1111-1111-111111111111";
  private static final String OTHER = "22222222-2222-2222-2222-222222222222";
  private static final String UNKNOWN = "33333333-3333-3333-3333-333333333333";
  private static final ObjectMapper JSON = new ObjectMapper();

  private DataSource dataSource;
  private SettingsService settings;
  private FakeAi fakeAi;
  private Routes routes;
  private UUID unitId;

  @BeforeEach
  void setUp() {
    dataSource = Fixtures.freshDataSource();
    Repositories repositories = new Repositories(dataSource);
    MutableClock clock = new MutableClock(Instant.parse("2026-10-21T01:00:00Z"), Fixtures.JAPAN);
    settings = new SettingsService(repositories.settings(), clock);
    fakeAi = new FakeAi();
    MockExamService mockExams =
        new MockExamService(
            settings, repositories.mockExams(), repositories.materials(), fakeAi, clock);
    routes = app -> new MockExamRoutes(mockExams).register(app);
    settings.save(
        UUID.fromString(USER),
        new SettingsCommand(
            StudentType.LOGICAL, Subject.MATH, UnitName.parse("二次関数").orElseThrow(), 3, true));
    settings.save(
        UUID.fromString(OTHER),
        new SettingsCommand(
            StudentType.LOGICAL, Subject.MATH, UnitName.parse("三角比").orElseThrow(), 3, true));
    unitId = settings.find(UUID.fromString(USER)).orElseThrow().unitId();
  }

  @Test
  void 機会があるとき_POST_は_201_で結果と偏差値の推移を返す() throws Exception {
    Fixtures.setLessonsSinceExam(dataSource, unitId, 3);

    HttpResponse<String> response = send("POST", "/api/mock-exams", USER);

    assertEquals(201, response.statusCode());
    JsonNode body = JSON.readTree(response.body());
    assertEquals("二次関数", body.get("unitName").asText());
    assertEquals(0, body.get("score").asInt());
    assertEquals("60.0", body.get("deviation").asText());
    assertEquals(1, body.get("history").size());
    assertEquals(
        body.get("deviation").asText(), body.get("history").get(0).get("deviation").asText());
  }

  @Test
  void 機会が無いときは_409_no_opportunity() throws Exception {
    Fixtures.setLessonsSinceExam(dataSource, unitId, 2);

    HttpResponse<String> response = send("POST", "/api/mock-exams", USER);

    assertEquals(409, response.statusCode());
    assertEquals("no_opportunity", error(response));
  }

  @Test
  void 外部_AI_が失敗したときは_502_mock_exam_failed_で_機会は残る() throws Exception {
    Fixtures.setLessonsSinceExam(dataSource, unitId, 3);
    fakeAi.script(
        UUID.fromString(USER),
        AiCallKind.MOCK_EXAM,
        List.of(new ScriptedResponse(false, null)),
        false);

    HttpResponse<String> response = send("POST", "/api/mock-exams", USER);

    assertEquals(502, response.statusCode());
    assertEquals("mock_exam_failed", error(response));
    assertEquals(3, queryInt("select lessons_since_exam from units where id = ?", unitId));
  }

  @Test
  void 結果は_利用者ごとに見え_ほかの利用者や無い模試は_404() throws Exception {
    Fixtures.setLessonsSinceExam(dataSource, unitId, 3);
    String id = JSON.readTree(send("POST", "/api/mock-exams", USER).body()).get("id").asText();

    HttpResponse<String> own = send("GET", "/api/mock-exams/" + id, USER);
    HttpResponse<String> other = send("GET", "/api/mock-exams/" + id, OTHER);
    HttpResponse<String> missing = send("GET", "/api/mock-exams/" + UUID.randomUUID(), USER);
    HttpResponse<String> malformed = send("GET", "/api/mock-exams/not-a-uuid", USER);

    assertEquals(200, own.statusCode());
    assertEquals(id, JSON.readTree(own.body()).get("id").asText());
    assertEquals(404, other.statusCode());
    assertEquals(404, missing.statusCode());
    assertEquals(404, malformed.statusCode());
  }

  @Test
  void 設定が無い利用者は_409_setup_required() throws Exception {
    HttpResponse<String> response = send("POST", "/api/mock-exams", UNKNOWN);

    assertEquals(409, response.statusCode());
    assertEquals("setup_required", error(response));
  }

  private static String error(HttpResponse<String> response) throws Exception {
    return JSON.readTree(response.body()).get("error").get("code").asText();
  }

  private int queryInt(String sql, Object... params) throws Exception {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        statement.setObject(i + 1, params[i]);
      }
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        return rows.getInt(1);
      }
    }
  }

  /** 模試の API を、Javalin を実際に起動して呼ぶ。 */
  private HttpResponse<String> send(String method, String path, String userId) throws Exception {
    Javalin app = WebApp.create(Optional.empty(), List.of(routes));
    app.start(0);
    try {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path))
              .header("Cookie", "teachme_uid=" + userId);
      HttpRequest request =
          ("POST".equals(method)
                  ? builder.POST(HttpRequest.BodyPublishers.noBody())
                  : builder.GET())
              .build();
      return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    } finally {
      app.stop();
    }
  }
}
