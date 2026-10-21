package com.teachme.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.teachme.ai.AiCallKind;
import com.teachme.ai.ScriptedResponse;
import com.teachme.ai.fake.FakeAi;
import com.teachme.application.MaterialOrganizer;
import com.teachme.application.MaterialService;
import com.teachme.application.SettingsService;
import com.teachme.application.SettingsService.SettingsCommand;
import com.teachme.application.TestControlService;
import com.teachme.domain.StudentType;
import com.teachme.domain.Subject;
import com.teachme.domain.UnitName;
import com.teachme.persistence.Repositories;
import com.teachme.testsupport.Await;
import com.teachme.testsupport.Fixtures;
import io.javalin.Javalin;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaterialRoutesTest {
  private static final String USER = "11111111-1111-1111-1111-111111111111";
  private static final String OTHER = "22222222-2222-2222-2222-222222222222";
  private static final String NO_SETTINGS = "33333333-3333-3333-3333-333333333333";
  private static final String BOUNDARY = "teachme-material-boundary";
  private static final byte[] PNG = {
    (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
  };
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  private FakeAi fakeAi;
  private MaterialOrganizer organizer;
  private Javalin app;

  @BeforeEach
  void setUp() {
    DataSource dataSource = Fixtures.freshDataSource();
    Repositories repositories = new Repositories(dataSource);
    Clock clock = Fixtures.clockAt("2026-10-21T10:00");
    SettingsService settings = new SettingsService(repositories.settings(), clock);
    settings.save(UUID.fromString(USER), command("二次関数"));
    settings.save(UUID.fromString(OTHER), command("力のはたらき"));
    fakeAi = new FakeAi();
    organizer = new MaterialOrganizer(repositories.materials(), fakeAi, clock);
    MaterialService materials =
        new MaterialService(settings, repositories.materials(), organizer, clock);
    app =
        WebApp.create(
            Optional.empty(),
            List.of(
                new MaterialRoutes(materials, organizer),
                new TestRoutes(new TestControlService(fakeAi))));
    app.start(0);
  }

  @AfterEach
  void tearDown() {
    app.stop();
    organizer.close();
  }

  private static SettingsCommand command(String unitName) {
    return new SettingsCommand(
        StudentType.LOGICAL, Subject.MATH, UnitName.parse(unitName).orElseThrow(), 3, true);
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
      if (userId != null) {
        request.header("Cookie", "teachme_uid=" + userId);
      }
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

  private HttpResponse<byte[]> get(String userId, String path) {
    return send("GET", path, userId, null, null);
  }

  /** 教材を登録する。`photoConfirmed` は、送る値をそのまま書く (null なら項目を送らない)。 */
  private HttpResponse<byte[]> register(String userId, String photoConfirmed, List<byte[]> images) {
    return send(
        "POST",
        "/api/materials",
        userId,
        "multipart/form-data; boundary=" + BOUNDARY,
        multipart(photoConfirmed, images));
  }

  private static byte[] multipart(String photoConfirmed, List<byte[]> images) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    if (photoConfirmed != null) {
      write(
          out,
          "--"
              + BOUNDARY
              + "\r\n"
              + "Content-Disposition: form-data; name=\"photoConfirmed\"\r\n\r\n"
              + photoConfirmed
              + "\r\n");
    }
    for (byte[] image : images) {
      write(
          out,
          "--"
              + BOUNDARY
              + "\r\n"
              + "Content-Disposition: form-data; name=\"images\"; filename=\"problem.png\"\r\n"
              + "Content-Type: image/png\r\n\r\n");
      write(out, image);
      write(out, "\r\n");
    }
    write(out, "--" + BOUNDARY + "--\r\n");
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

  private static String errorCode(HttpResponse<byte[]> response) {
    return json(response).get("error").get("code").asText();
  }

  /** 登録した教材の、現在の状態。 */
  private String statusOf(String userId, String materialId) {
    return json(get(userId, "/api/materials/" + materialId)).get("status").asText();
  }

  private int organizeCalls(String userId) {
    HttpResponse<byte[]> response = get(userId, "/api/test/ai-calls");
    return json(response).get("counts").get(AiCallKind.ORGANIZE.wireName()).asInt();
  }

  private String registerAndGetId(String userId) {
    HttpResponse<byte[]> response = register(userId, "true", List.of(PNG));
    assertEquals(202, response.statusCode());
    return json(response).get("id").asText();
  }

  @Test
  void 写真の確認が無いと_400_になり_画像は外部へ送らない() {
    HttpResponse<byte[]> response = register(USER, "false", List.of(PNG));

    assertEquals(400, response.statusCode());
    assertEquals("photo_not_confirmed", errorCode(response));
    assertEquals(0, fakeAi.callCounts(UUID.fromString(USER)).get(AiCallKind.ORGANIZE));
  }

  @Test
  void 画像が_0_枚や_4_枚のときは_400_invalid_images_になる() {
    HttpResponse<byte[]> none = register(USER, "true", List.of());
    HttpResponse<byte[]> four = register(USER, "true", List.of(PNG, PNG, PNG, PNG));

    assertEquals(400, none.statusCode());
    assertEquals("invalid_images", errorCode(none));
    assertEquals(400, four.statusCode());
    assertTrue(json(four).get("error").get("message").asText().contains("1 枚から 3 枚まで"));
  }

  @Test
  void 画像の形式が違うと_400_invalid_images_になる() {
    HttpResponse<byte[]> response =
        register(USER, "true", List.of("hello".getBytes(StandardCharsets.UTF_8)));

    assertEquals(400, response.statusCode());
    assertEquals("invalid_images", errorCode(response));
  }

  @Test
  void 登録は_202_で整理中を返し_整理が終わると結果が出る_整理の呼び出しは_2_回を超えない() throws InterruptedException {
    String materialId = registerAndGetId(USER);

    Await.until("整理済み", () -> statusOf(USER, materialId).equals("organized"));

    JsonNode detail = json(get(USER, "/api/materials/" + materialId));
    assertEquals(1, detail.get("number").asInt());
    assertEquals(3, detail.get("problem").get("teachingPoints").size());
    assertTrue(detail.get("problem").get("figures").isArray());
    assertEquals(1, organizeCalls(USER));
  }

  @Test
  void 整理が_2_回失敗すると_整理できませんでしたになり_やり直すと整理済みになり_もう一度は_409() throws InterruptedException {
    fakeAi.script(
        UUID.fromString(USER),
        AiCallKind.ORGANIZE,
        List.of(new ScriptedResponse(false, null), new ScriptedResponse(false, null)),
        false);
    String materialId = registerAndGetId(USER);
    Await.until("整理できませんでした", () -> statusOf(USER, materialId).equals("failed"));
    // 自動のやり直しを含めて、1 回の登録で organize は 2 回まで
    assertEquals(2, organizeCalls(USER));

    HttpResponse<byte[]> retry =
        send("POST", "/api/materials/" + materialId + "/retry", USER, null, null);
    assertEquals(202, retry.statusCode());
    assertEquals("organizing", json(retry).get("status").asText());
    Await.until("整理済み", () -> statusOf(USER, materialId).equals("organized"));

    HttpResponse<byte[]> again =
        send("POST", "/api/materials/" + materialId + "/retry", USER, null, null);
    assertEquals(409, again.statusCode());
    assertEquals("material_not_failed", errorCode(again));
  }

  @Test
  void ほかの利用者の教材は_404_になり_削除も画像の取得もできない() {
    String materialId = registerAndGetId(USER);

    assertEquals(404, get(OTHER, "/api/materials/" + materialId).statusCode());
    assertEquals(404, get(OTHER, "/api/materials/" + materialId + "/images/1").statusCode());
    assertEquals(
        404, send("DELETE", "/api/materials/" + materialId, OTHER, null, null).statusCode());
    assertEquals(200, get(USER, "/api/materials/" + materialId).statusCode());
  }

  @Test
  void 削除は何度呼んでも_204_で_一覧から消える() {
    String materialId = registerAndGetId(USER);

    assertEquals(
        204, send("DELETE", "/api/materials/" + materialId, USER, null, null).statusCode());
    assertEquals(
        204, send("DELETE", "/api/materials/" + materialId, USER, null, null).statusCode());

    assertEquals(0, json(get(USER, "/api/materials")).get("materials").size());
    assertEquals(404, get(USER, "/api/materials/" + materialId).statusCode());
  }

  @Test
  void 画像は番号で取得でき_範囲の外の番号は_404_になる() {
    String materialId = registerAndGetId(USER);

    HttpResponse<byte[]> image = get(USER, "/api/materials/" + materialId + "/images/1");

    assertEquals(200, image.statusCode());
    assertTrue(image.headers().firstValue("Content-Type").orElseThrow().startsWith("image/png"));
    assertArrayEquals(PNG, image.body());
    assertEquals(404, get(USER, "/api/materials/" + materialId + "/images/0").statusCode());
    assertEquals(404, get(USER, "/api/materials/" + materialId + "/images/4").statusCode());
    assertEquals(404, get(USER, "/api/materials/" + materialId + "/images/abc").statusCode());
  }

  @Test
  void 設定が無い利用者の一覧は_409_setup_required_になる() {
    HttpResponse<byte[]> response = get(NO_SETTINGS, "/api/materials");

    assertEquals(409, response.statusCode());
    assertEquals("setup_required", errorCode(response));
  }
}
