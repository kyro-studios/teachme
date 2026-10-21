package com.teachme.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.teachme.web.Json;
import com.teachme.web.WebApp;
import io.javalin.testtools.JavalinTest;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MainRoutesTest {
  /** 組み立ての確認では DB に接続しないため、データソースは使わない。 */
  private static final Components WITHOUT_DATABASE =
      new Components(Clock.systemDefaultZone(), null);

  @Test
  void fake_のときは_テスト用の_API_を登録する() {
    AppConfig config = AppConfig.fromEnv(Map.of("AI_MODE", "fake"));

    JavalinTest.test(
        WebApp.create(Optional.empty(), Main.routes(config, WITHOUT_DATABASE)),
        (server, client) -> {
          assertEquals(200, client.get("/api/test/ai-calls").code());
        });
  }

  @Test
  void fake_のときは_形の合わない_payload_を_400_にし_形の合う_payload_は_受け付ける() throws Exception {
    AppConfig config = AppConfig.fromEnv(Map.of("AI_MODE", "fake"));

    JavalinTest.test(
        WebApp.create(Optional.empty(), Main.routes(config, WITHOUT_DATABASE)),
        (server, client) -> {
          var invalid =
              client.put(
                  "/api/test/ai-script",
                  "{\"kind\":\"evaluation\",\"responses\":[{\"outcome\":\"success\",\"payload\":{\"degrees\":[1]}}]}");
          var valid =
              client.put(
                  "/api/test/ai-script",
                  "{\"kind\":\"evaluation\",\"responses\":[{\"outcome\":\"success\",\"payload\":{\"degrees\":[2,2,2]}}]}");

          assertEquals(400, invalid.code());
          assertEquals(
              "validation_error",
              Json.mapper().readTree(invalid.body().string()).get("error").get("code").asText());
          assertEquals(204, valid.code());
        });
  }

  @Test
  void real_のときは_テスト用の_API_を登録せず_404_を返す() {
    AppConfig config =
        AppConfig.fromEnv(
            Map.of(
                "AI_MODE", "real",
                "LLM_API_KEY", "test-key",
                "LLM_MODEL_ORGANIZE", "organize-model",
                "LLM_MODEL_DIALOGUE", "dialogue-model",
                "ELEVENLABS_API_KEY", "eleven-key",
                "ELEVENLABS_VOICE_IDS", "intuitive=a,diligent=b,logical=c,mentor=d",
                "ELEVENLABS_STT_MODEL", "stt-model",
                "ELEVENLABS_TTS_MODEL", "tts-model"));

    JavalinTest.test(
        WebApp.create(Optional.empty(), Main.routes(config, WITHOUT_DATABASE)),
        (server, client) -> {
          assertEquals(404, client.get("/api/test/ai-calls").code());
          assertEquals(404, client.put("/api/test/ai-script", "{}").code());
          assertEquals(404, client.delete("/api/test/ai-script").code());
          assertEquals(404, client.put("/api/test/limits", "{}").code());
          assertEquals("{\"aiMode\":\"real\"}", client.get("/api/config").body().string());
        });
  }
}
