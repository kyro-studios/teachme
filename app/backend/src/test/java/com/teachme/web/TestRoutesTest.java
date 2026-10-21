package com.teachme.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.teachme.ai.AiCallKind;
import com.teachme.ai.AiScriptControl;
import com.teachme.ai.ScriptedResponse;
import com.teachme.application.TestControlService;
import io.javalin.testtools.JavalinTest;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TestRoutesTest {
  private static final String USER = "11111111-1111-1111-1111-111111111111";

  /** 呼び出しを覚えるだけの、AiScriptControl の代わり。 */
  private static final class RecordingScript implements AiScriptControl {
    UUID userId;
    AiCallKind kind;
    List<ScriptedResponse> responses;
    boolean repeat;
    UUID resetUser;
    final Map<AiCallKind, Integer> counts = new EnumMap<>(AiCallKind.class);

    @Override
    public void script(
        UUID userId, AiCallKind kind, List<ScriptedResponse> responses, boolean repeat) {
      this.userId = userId;
      this.kind = kind;
      this.responses = responses;
      this.repeat = repeat;
    }

    @Override
    public void reset(UUID userId) {
      resetUser = userId;
    }

    @Override
    public Map<AiCallKind, Integer> callCounts(UUID userId) {
      return counts;
    }
  }

  private static String cookie(String userId) {
    return "teachme_uid=" + userId;
  }

  private static String errorCode(String body) throws Exception {
    return Json.mapper().readTree(body).get("error").get("code").asText();
  }

  @Test
  void ai_script_の_PUT_は_204_で_呼んだ利用者の待ち列を渡す() {
    RecordingScript script = new RecordingScript();
    Routes routes = app -> new TestRoutes(new TestControlService(script)).register(app);

    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(routes)),
        (server, client) -> {
          var response =
              client.put(
                  "/api/test/ai-script",
                  "{\"kind\":\"student_reply\",\"repeat\":true,\"responses\":[{\"outcome\":\"failure\"}]}",
                  request -> request.header("Cookie", cookie(USER)));

          assertEquals(204, response.code());
          assertEquals(UUID.fromString(USER), script.userId);
          assertEquals(AiCallKind.STUDENT_REPLY, script.kind);
          assertTrue(script.repeat);
          assertFalse(script.responses.get(0).success());
        });
  }

  @Test
  void ai_script_の_PUT_は_知らない_kind_と_空の応答を_400_にする() {
    RecordingScript script = new RecordingScript();
    Routes routes = app -> new TestRoutes(new TestControlService(script)).register(app);

    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(routes)),
        (server, client) -> {
          var unknownKind =
              client.put(
                  "/api/test/ai-script",
                  "{\"kind\":\"unknown\",\"responses\":[{\"outcome\":\"success\"}]}",
                  request -> request.header("Cookie", cookie(USER)));
          var noResponses =
              client.put(
                  "/api/test/ai-script",
                  "{\"kind\":\"stt\",\"responses\":[]}",
                  request -> request.header("Cookie", cookie(USER)));
          var unknownOutcome =
              client.put(
                  "/api/test/ai-script",
                  "{\"kind\":\"stt\",\"responses\":[{\"outcome\":\"maybe\"}]}",
                  request -> request.header("Cookie", cookie(USER)));

          assertEquals(400, unknownKind.code());
          assertEquals("validation_error", errorCode(unknownKind.body().string()));
          assertEquals(400, noResponses.code());
          assertEquals(400, unknownOutcome.code());
          assertNull(script.kind);
        });
  }

  @Test
  void ai_script_の_本文が_JSON_でなければ_400_にする() {
    Routes routes =
        app -> new TestRoutes(new TestControlService(new RecordingScript())).register(app);

    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(routes)),
        (server, client) -> {
          var response = client.put("/api/test/ai-script", "{");

          assertEquals(400, response.code());
          assertEquals("invalid_request", errorCode(response.body().string()));
        });
  }

  @Test
  void ai_script_の_DELETE_は_204_で_呼んだ利用者の分を消す() {
    RecordingScript script = new RecordingScript();
    Routes routes = app -> new TestRoutes(new TestControlService(script)).register(app);

    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(routes)),
        (server, client) -> {
          var response =
              client.delete(
                  "/api/test/ai-script", null, request -> request.header("Cookie", cookie(USER)));

          assertEquals(204, response.code());
          assertEquals(UUID.fromString(USER), script.resetUser);
        });
  }

  @Test
  void ai_calls_は_kind_ごとの回数を_counts_で返す() throws Exception {
    RecordingScript script = new RecordingScript();
    script.counts.put(AiCallKind.STT, 2);
    Routes routes = app -> new TestRoutes(new TestControlService(script)).register(app);

    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(routes)),
        (server, client) -> {
          var response =
              client.get("/api/test/ai-calls", request -> request.header("Cookie", cookie(USER)));

          assertEquals(200, response.code());
          var counts = Json.mapper().readTree(response.body().string()).get("counts");
          assertEquals(2, counts.get("stt").asInt());
          assertEquals(0, counts.get("tts").asInt());
          assertEquals(AiCallKind.values().length, counts.size());
        });
  }

  @Test
  void limits_の_PUT_は_範囲外を_400_にし_範囲内なら_204_で上書きする() throws Exception {
    TestControlService service = new TestControlService(new RecordingScript());
    Routes routes = app -> new TestRoutes(service).register(app);

    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(routes)),
        (server, client) -> {
          var invalid =
              client.put(
                  "/api/test/limits",
                  "{\"lessonSeconds\":0}",
                  request -> request.header("Cookie", cookie(USER)));
          var valid =
              client.put(
                  "/api/test/limits",
                  "{\"lessonSeconds\":5}",
                  request -> request.header("Cookie", cookie(USER)));

          assertEquals(400, invalid.code());
          assertTrue(
              Json.mapper()
                  .readTree(invalid.body().string())
                  .get("error")
                  .get("fields")
                  .has("lessonSeconds"));
          assertEquals(204, valid.code());
          assertEquals(
              Duration.ofSeconds(5), service.lessonLimits(UUID.fromString(USER)).duration());
        });
  }
}
