package com.teachme.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.teachme.application.SettingsService;
import com.teachme.persistence.Repositories;
import com.teachme.testsupport.Fixtures;
import io.javalin.testtools.JavalinTest;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettingsRoutesTest {
  private static final String USER = "11111111-1111-1111-1111-111111111111";
  private static final String OTHER = "22222222-2222-2222-2222-222222222222";
  private static final String VALID =
      "{\"studentTypeId\":\"logical\",\"subject\":\"math\",\"unitName\":\"二次関数\","
          + "\"lessonsPerWeek\":3,\"privacyConfirmed\":true}";

  private Routes routes;

  @BeforeEach
  void setUp() {
    DataSource dataSource = Fixtures.freshDataSource();
    Clock clock = Fixtures.clockAt("2026-10-21T10:00");
    SettingsService settings = new SettingsService(new Repositories(dataSource).settings(), clock);
    routes = app -> new SettingsRoutes(settings).register(app);
  }

  private static String cookie(String userId) {
    return "teachme_uid=" + userId;
  }

  private static String errorCode(String body) throws Exception {
    return Json.mapper().readTree(body).get("error").get("code").asText();
  }

  @Test
  void 生徒の種類は_3_つを_仕様の順で返す() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(routes)),
        (server, client) -> {
          var response = client.get("/api/student-types");

          assertEquals(200, response.code());
          assertEquals(
              "{\"studentTypes\":["
                  + "{\"id\":\"intuitive\",\"displayName\":\"直感・感覚派\",\"initialDeviation\":45,\"targetDeviation\":50},"
                  + "{\"id\":\"diligent\",\"displayName\":\"真面目な暗記派\",\"initialDeviation\":52,\"targetDeviation\":58},"
                  + "{\"id\":\"logical\",\"displayName\":\"論理・ツッコミ派\",\"initialDeviation\":60,\"targetDeviation\":65}"
                  + "]}",
              response.body().string());
        });
  }

  @Test
  void 設定が無ければ_completed_は_false_で_settings_は_null() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(routes)),
        (server, client) -> {
          var response =
              client.get("/api/settings", request -> request.header("Cookie", cookie(USER)));

          assertEquals(200, response.code());
          assertEquals("{\"completed\":false,\"settings\":null}", response.body().string());
        });
  }

  @Test
  void 個人情報の確認が無いと_400_で_項目を返し_保存しない() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(routes)),
        (server, client) -> {
          var put =
              client.put(
                  "/api/settings",
                  VALID.replace("\"privacyConfirmed\":true", "\"privacyConfirmed\":false"),
                  request -> request.header("Cookie", cookie(USER)));
          var get = client.get("/api/settings", request -> request.header("Cookie", cookie(USER)));

          assertEquals(400, put.code());
          String body = put.body().string();
          assertEquals("validation_error", errorCode(body));
          assertTrue(body.contains("\"privacyConfirmed\""), body);
          assertTrue(get.body().string().contains("\"completed\":false"));
        });
  }

  @Test
  void 入力が無効なら_項目ごとの文言を返す() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(routes)),
        (server, client) -> {
          var response =
              client.put(
                  "/api/settings",
                  "{\"subject\":\"english\",\"unitName\":\"   \",\"lessonsPerWeek\":8,\"privacyConfirmed\":true}",
                  request -> request.header("Cookie", cookie(USER)));

          assertEquals(400, response.code());
          String body = response.body().string();
          assertEquals("validation_error", errorCode(body));
          assertTrue(body.contains("生徒を 1 人選んでください"), body);
          assertTrue(body.contains("教科を選んでください"), body);
          assertTrue(body.contains("単元名は 1 文字以上 30 文字以内で入力してください"), body);
          assertTrue(body.contains("週の授業回数を選んでください"), body);
        });
  }

  @Test
  void 有効な設定を保存すると_完了になり_開き直しても同じ内容を返す() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(routes)),
        (server, client) -> {
          var put =
              client.put("/api/settings", VALID, request -> request.header("Cookie", cookie(USER)));
          var get = client.get("/api/settings", request -> request.header("Cookie", cookie(USER)));

          assertEquals(200, put.code());
          assertTrue(put.body().string().contains("\"completed\":true"));
          String body = get.body().string();
          assertTrue(body.contains("\"studentTypeId\":\"logical\""), body);
          assertTrue(body.contains("\"subject\":\"math\""), body);
          assertTrue(body.contains("\"unitName\":\"二次関数\""), body);
          assertTrue(body.contains("\"lessonsPerWeek\":3"), body);
          assertTrue(body.contains("\"privacyConfirmed\":true"), body);
        });
  }

  @Test
  void 別の利用者の設定は見えない() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(routes)),
        (server, client) -> {
          client.put("/api/settings", VALID, request -> request.header("Cookie", cookie(USER)));

          var other =
              client.get("/api/settings", request -> request.header("Cookie", cookie(OTHER)));

          assertEquals("{\"completed\":false,\"settings\":null}", other.body().string());
        });
  }
}
