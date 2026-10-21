package com.teachme.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.teachme.ai.AiCallKind;
import com.teachme.ai.ScriptedResponse;
import com.teachme.ai.fake.FakeAi;
import com.teachme.application.HomeService;
import com.teachme.application.HomeworkService;
import com.teachme.application.SettingsService;
import com.teachme.application.SettingsService.SettingsCommand;
import com.teachme.domain.StudentType;
import com.teachme.domain.Subject;
import com.teachme.domain.UnitName;
import com.teachme.persistence.Repositories;
import com.teachme.testsupport.Fixtures;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HomeRoutesTest {
  private static final String USER = "11111111-1111-1111-1111-111111111111";
  private static final String OTHER = "22222222-2222-2222-2222-222222222222";
  private static final String BOUNDARY = "teachme-test-boundary";
  private static final String SETTINGS =
      "{\"studentTypeId\":\"logical\",\"subject\":\"math\",\"unitName\":\"二次関数\","
          + "\"lessonsPerWeek\":3,\"privacyConfirmed\":true}";
  private static final byte[] PNG = {
    (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
  };

  private DataSource dataSource;
  private SettingsService settings;
  private FakeAi fakeAi;
  private Routes routes;

  @BeforeEach
  void setUp() {
    dataSource = Fixtures.freshDataSource();
    Repositories repositories = new Repositories(dataSource);
    Clock clock = Fixtures.clockAt("2026-10-21T10:00");
    settings = new SettingsService(repositories.settings(), clock);
    HomeService home =
        new HomeService(
            settings,
            repositories.lessonRecords(),
            repositories.todayModes(),
            repositories.units(),
            clock);
    fakeAi = new FakeAi();
    HomeworkService homework =
        new HomeworkService(settings, repositories.todayModes(), fakeAi, clock);
    routes = app -> new HomeRoutes(home, homework).register(app);
    // ホームのテストの前提の設定は、サービスで保存する (設定の API は SettingsRoutesTest で確かめる)
    settings.save(
        UUID.fromString(USER),
        new SettingsCommand(
            StudentType.LOGICAL, Subject.MATH, UnitName.parse("二次関数").orElseThrow(), 3, true));
  }

  private static String cookie(String userId) {
    return "teachme_uid=" + userId;
  }

  private static byte[] multipart(String confirmed, byte[] photo) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(
        part(
            "--"
                + BOUNDARY
                + "\r\n"
                + "Content-Disposition: form-data; name=\"photoConfirmed\"\r\n\r\n"
                + confirmed
                + "\r\n"));
    out.write(
        part(
            "--"
                + BOUNDARY
                + "\r\n"
                + "Content-Disposition: form-data; name=\"photo\"; filename=\"homework.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n"));
    out.write(photo);
    out.write(part("\r\n--" + BOUNDARY + "--\r\n"));
    return out.toByteArray();
  }

  private static byte[] part(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  /** 宿題の写真を送る。Javalin を実際に起動し、multipart のリクエストを送る。 */
  private HttpResponse<String> postHomework(String userId, String confirmed, byte[] photo)
      throws Exception {
    Javalin app = WebApp.create(Optional.empty(), List.of(routes));
    app.start(0);
    try {
      HttpRequest request =
          HttpRequest.newBuilder(
                  URI.create("http://127.0.0.1:" + app.port() + "/api/homework-checks"))
              .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
              .header("Cookie", cookie(userId))
              .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(confirmed, photo)))
              .build();
      return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    } finally {
      app.stop();
    }
  }

  @Test
  void 設定が無いと_ホームは_409_setup_required_になる() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(routes)),
        (server, client) -> {
          var response =
              client.get("/api/home", request -> request.header("Cookie", cookie(OTHER)));

          assertEquals(409, response.code());
          assertTrue(response.body().string().contains("\"code\":\"setup_required\""));
        });
  }

  @Test
  void 設定の後のホームは_担当と単元と今週の授業を返す() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(routes)),
        (server, client) -> {
          var response = client.get("/api/home", request -> request.header("Cookie", cookie(USER)));

          assertEquals(200, response.code());
          String body = response.body().string();
          assertTrue(body.contains("\"displayName\":\"論理・ツッコミ派\""), body);
          assertTrue(body.contains("\"unitName\":\"二次関数\""), body);
          assertTrue(body.contains("\"lessonsThisWeek\":0"), body);
          assertTrue(body.contains("\"lessonsPerWeek\":3"), body);
          assertTrue(body.contains("\"todayMode\":\"normal\""), body);
          assertTrue(body.contains("\"hasOrganizedMaterial\":false"), body);
        });
  }

  @Test
  void 今日のモードを切り替えると_その日のホームに反映される() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(routes)),
        (server, client) -> {
          var put =
              client.put(
                  "/api/today-mode",
                  "{\"mode\":\"self_study\"}",
                  request -> request.header("Cookie", cookie(USER)));
          var get = client.get("/api/home", request -> request.header("Cookie", cookie(USER)));

          assertEquals(200, put.code());
          assertTrue(put.body().string().contains("\"todayMode\":\"self_study\""));
          assertTrue(get.body().string().contains("\"todayMode\":\"self_study\""));
        });
  }

  @Test
  void 今日のモードに知らない値を入れると_400_になる() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(routes)),
        (server, client) -> {
          var response =
              client.put(
                  "/api/today-mode",
                  "{\"mode\":\"sleep\"}",
                  request -> request.header("Cookie", cookie(USER)));

          assertEquals(400, response.code());
          assertTrue(response.body().string().contains("\"mode\""));
        });
  }

  @Test
  void 写真の確認が無いと_400_で_写真を判定しない() throws Exception {
    fakeAi.script(
        UUID.fromString(USER),
        AiCallKind.HOMEWORK,
        List.of(new ScriptedResponse(true, Map.of("verdict", "correct", "comment", "正解"))),
        false);

    HttpResponse<String> response = postHomework(USER, "false", PNG);

    assertEquals(400, response.statusCode());
    assertTrue(response.body().contains("\"code\":\"photo_not_confirmed\""), response.body());
    assertEquals(0, fakeAi.callCounts(UUID.fromString(USER)).get(AiCallKind.HOMEWORK));
  }

  @Test
  void 写真が_JPEG_か_PNG_でなければ_400_になる() throws Exception {
    byte[] text = "not an image".getBytes(StandardCharsets.UTF_8);

    HttpResponse<String> response = postHomework(USER, "true", text);

    assertEquals(400, response.statusCode());
    assertTrue(response.body().contains("\"code\":\"invalid_images\""), response.body());
  }

  @Test
  void 今日のモードが宿題の丸つけでなければ_409_になる() throws Exception {
    HttpResponse<String> response = postHomework(USER, "true", PNG);

    assertEquals(409, response.statusCode());
    assertTrue(response.body().contains("\"code\":\"mode_not_homework_check\""), response.body());
  }

  @Test
  void 宿題の丸つけでは_判定の結果を返す() throws Exception {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(routes)),
        (server, client) ->
            client.put(
                "/api/today-mode",
                "{\"mode\":\"homework_check\"}",
                request -> request.header("Cookie", cookie(USER))));

    HttpResponse<String> response = postHomework(USER, "true", PNG);

    assertEquals(200, response.statusCode());
    assertEquals("{\"verdict\":\"correct\",\"comment\":\"よくできました\"}", response.body());
  }

  @Test
  void 外部_AI_が失敗すると_判定できなかったものとして返す() throws Exception {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(routes)),
        (server, client) ->
            client.put(
                "/api/today-mode",
                "{\"mode\":\"homework_check\"}",
                request -> request.header("Cookie", cookie(USER))));
    fakeAi.script(
        UUID.fromString(USER),
        AiCallKind.HOMEWORK,
        List.of(new ScriptedResponse(false, null)),
        false);

    HttpResponse<String> response = postHomework(USER, "true", PNG);

    assertEquals(200, response.statusCode());
    assertEquals("{\"verdict\":\"undetermined\",\"comment\":\"\"}", response.body());
  }
}
