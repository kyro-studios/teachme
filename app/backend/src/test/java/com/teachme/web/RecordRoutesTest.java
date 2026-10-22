package com.teachme.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teachme.application.RecordService;
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
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecordRoutesTest {
  private static final String USER = "11111111-1111-1111-1111-111111111111";
  private static final String OTHER = "22222222-2222-2222-2222-222222222222";
  private static final String UNKNOWN = "33333333-3333-3333-3333-333333333333";
  private static final ObjectMapper JSON = new ObjectMapper();

  private DataSource dataSource;
  private Routes routes;
  private UUID unitId;
  private UUID recordId;

  @BeforeEach
  void setUp() throws SQLException {
    dataSource = Fixtures.freshDataSource();
    Repositories repositories = new Repositories(dataSource);
    MutableClock clock = new MutableClock(Instant.parse("2026-10-21T01:00:00Z"), Fixtures.JAPAN);
    SettingsService settings = new SettingsService(repositories.settings(), clock);
    RecordService records = new RecordService(settings, repositories.records());
    routes = app -> new RecordRoutes(records).register(app);
    save(settings, USER);
    save(settings, OTHER);
    unitId = settings.find(UUID.fromString(USER)).orElseThrow().unitId();
    recordId = UUID.randomUUID();
    execute(
        "insert into lesson_records (id, user_id, unit_id, material_ref, completed_at, degrees,"
            + " summary_text, quotes) values (?, ?, ?, ?, ?, '{2,2,1}', 'まとめの文', ?::jsonb)",
        recordId,
        UUID.fromString(USER),
        unitId,
        UUID.randomUUID(),
        OffsetDateTime.ofInstant(Instant.parse("2026-10-20T01:00:00Z"), ZoneOffset.UTC),
        "[\"板書の文\"]");
  }

  @Test
  void 単元の一覧は_記録のある単元を返す() throws Exception {
    HttpResponse<String> response = send("GET", "/api/records/units", USER);

    assertEquals(200, response.statusCode());
    JsonNode unit = JSON.readTree(response.body()).get("units").get(0);
    assertEquals(unitId.toString(), unit.get("unitId").asText());
    assertEquals("math", unit.get("subject").asText());
    assertEquals("二次関数", unit.get("unitName").asText());
    assertEquals(1, unit.get("lessonCount").asInt());
    assertEquals(0, unit.get("mockExamCount").asInt());
  }

  @Test
  void 単元の画面は_学習レポートを返し_模試が無いときは_latestMockExam_を_null_にする() throws Exception {
    HttpResponse<String> response = send("GET", "/api/records/units/" + unitId, USER);

    assertEquals(200, response.statusCode());
    JsonNode body = JSON.readTree(response.body());
    assertEquals("二次関数", body.get("unitName").asText());
    assertEquals(recordId.toString(), body.get("notes").get(0).get("recordId").asText());
    assertTrue(body.get("mockExams").isEmpty());
    JsonNode report = body.get("report");
    assertEquals(1, report.get("lessonCount").asInt());
    assertEquals(1, report.get("taughtProblemCount").asInt());
    assertTrue(report.has("latestMockExam"));
    assertTrue(report.get("latestMockExam").isNull());
  }

  @Test
  void まとめノートは_内容と引用する板書を返す() throws Exception {
    HttpResponse<String> response = send("GET", "/api/records/notes/" + recordId, USER);

    assertEquals(200, response.statusCode());
    JsonNode body = JSON.readTree(response.body());
    assertEquals("まとめの文", body.get("summaryText").asText());
    assertEquals("板書の文", body.get("quotes").get(0).asText());
    assertEquals("二次関数", body.get("unitName").asText());
  }

  @Test
  void 削除は_204_で_2_回呼んでも同じ_そのあと記録は無くなる() throws Exception {
    HttpResponse<String> first = send("DELETE", "/api/records/units/" + unitId, USER);
    HttpResponse<String> second = send("DELETE", "/api/records/units/" + unitId, USER);

    assertEquals(204, first.statusCode());
    assertEquals(204, second.statusCode());
    assertEquals(0, queryInt("select count(*) from lesson_records where unit_id = ?", unitId));
    assertEquals("{\"units\":[]}", send("GET", "/api/records/units", USER).body());
  }

  @Test
  void ほかの利用者の識別子と_形式の不正な識別子は_404_で_何も消さない() throws Exception {
    String foreignUnit = "/api/records/units/" + unitId;

    assertEquals(404, send("GET", foreignUnit, OTHER).statusCode());
    assertEquals(404, send("DELETE", foreignUnit, OTHER).statusCode());
    assertEquals(404, send("GET", "/api/records/notes/" + recordId, OTHER).statusCode());
    assertEquals(404, send("GET", "/api/records/units/not-a-uuid", USER).statusCode());
    assertEquals(404, send("DELETE", "/api/records/units/not-a-uuid", USER).statusCode());
    assertEquals(404, send("GET", "/api/records/notes/" + UUID.randomUUID(), USER).statusCode());
    assertEquals(1, queryInt("select count(*) from lesson_records where unit_id = ?", unitId));
  }

  @Test
  void 設定が無い利用者は_409_setup_required() throws Exception {
    HttpResponse<String> response = send("GET", "/api/records/units", UNKNOWN);

    assertEquals(409, response.statusCode());
    assertEquals(
        "setup_required", JSON.readTree(response.body()).get("error").get("code").asText());
  }

  private static void save(SettingsService settings, String userId) {
    settings.save(
        UUID.fromString(userId),
        new SettingsCommand(
            StudentType.LOGICAL, Subject.MATH, UnitName.parse("二次関数").orElseThrow(), 3, true));
  }

  private void execute(String sql, Object... params) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        statement.setObject(i + 1, params[i]);
      }
      statement.executeUpdate();
    }
  }

  private int queryInt(String sql, Object... params) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        statement.setObject(i + 1, params[i]);
      }
      try (var rows = statement.executeQuery()) {
        rows.next();
        return rows.getInt(1);
      }
    }
  }

  /** 学習記録の API を、Javalin を実際に起動して呼ぶ。 */
  private HttpResponse<String> send(String method, String path, String userId) throws Exception {
    Javalin app = WebApp.create(Optional.empty(), List.of(routes));
    app.start(0);
    try {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path))
              .header("Cookie", "teachme_uid=" + userId);
      HttpRequest request =
          switch (method) {
            case "DELETE" -> builder.DELETE().build();
            default -> builder.GET().build();
          };
      return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    } finally {
      app.stop();
    }
  }
}
