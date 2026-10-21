package com.teachme.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.testtools.JavalinTest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApiErrorsTest {
  record Body(String name) {}

  private static final Routes ERRORS =
      app -> {
        app.get(
            "/api/api-error",
            ctx -> {
              throw new ApiException(
                  400, "invalid_name", "名前を入力してください", Map.of("name", "名前を入力してください"));
            });
        app.get(
            "/api/field-error",
            ctx ->
                new FieldErrors()
                    .add("name", "名前を入力してください")
                    .throwIfAny("invalid_input", "入力に誤りがあります"));
        app.get(
            "/api/unexpected",
            ctx -> {
              throw new IllegalStateException("本文の一部: 秘密の発話");
            });
        app.post("/api/body", ctx -> ctx.result(RequestBodies.readJson(ctx, Body.class).name()));
      };

  @Test
  void 未知の_api_のパスは_JSON_の_404_を返す() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(ERRORS)),
        (server, client) -> {
          var response = client.get("/api/unknown");

          assertEquals(404, response.code());
          assertTrue(response.header("Content-Type").startsWith("application/json"));
          JsonNode error = json(response.body().string()).get("error");
          assertEquals("not_found", error.get("code").asText());
          assertEquals("対象が見つかりません", error.get("message").asText());
          assertFalse(error.has("fields"));
        });
  }

  @Test
  void POST_でも未知の_api_のパスは_JSON_の_404_を返す() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(ERRORS)),
        (server, client) -> {
          var response = client.post("/api/unknown", null);

          assertEquals(404, response.code());
          assertEquals("not_found", json(response.body().string()).at("/error/code").asText());
        });
  }

  @Test
  void ApiException_は_status_code_message_fields_の形で返す() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(ERRORS)),
        (server, client) -> {
          var response = client.get("/api/api-error");

          assertEquals(400, response.code());
          JsonNode error = json(response.body().string()).get("error");
          assertEquals("invalid_name", error.get("code").asText());
          assertEquals("名前を入力してください", error.get("message").asText());
          assertEquals("名前を入力してください", error.at("/fields/name").asText());
        });
  }

  @Test
  void FieldErrors_は_項目の誤りを_fields_に入れて_400_を返す() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(ERRORS)),
        (server, client) -> {
          var response = client.get("/api/field-error");

          assertEquals(400, response.code());
          JsonNode error = json(response.body().string()).get("error");
          assertEquals("invalid_input", error.get("code").asText());
          assertEquals("名前を入力してください", error.at("/fields/name").asText());
        });
  }

  @Test
  void 想定しない例外は_500_で返し_例外の文言を出さない() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(ERRORS)),
        (server, client) -> {
          var response = client.get("/api/unexpected");

          assertEquals(500, response.code());
          String body = response.body().string();
          assertEquals("internal_error", json(body).at("/error/code").asText());
          assertFalse(body.contains("秘密の発話"), body);
        });
  }

  @Test
  void JSON_として読めない本文は_400_の_invalid_request_を返す() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(ERRORS)),
        (server, client) -> {
          var response = client.post("/api/body", "{");

          assertEquals(400, response.code());
          JsonNode error = json(response.body().string()).get("error");
          assertEquals("invalid_request", error.get("code").asText());
          assertFalse(error.has("fields"));
        });
  }

  @Test
  void JSON_として読めた本文は_型に読み取る() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(ERRORS)),
        (server, client) -> {
          var response = client.post("/api/body", "{\"name\":\"太郎\"}");

          assertEquals(200, response.code());
          assertEquals("太郎", response.body().string());
        });
  }

  private static JsonNode json(String body) {
    try {
      return Json.mapper().readTree(body);
    } catch (IOException e) {
      throw new AssertionError("JSON として読めません: " + body, e);
    }
  }
}
