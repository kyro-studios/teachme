package com.teachme.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.testtools.JavalinTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebAppTest {
  @Test
  void healthz_は_ok_を返す() {
    JavalinTest.test(
        WebApp.create(Optional.empty()),
        (server, client) -> {
          var response = client.get("/api/healthz");
          assertEquals(200, response.code());
          assertEquals("ok", response.body().string());
        });
  }

  @Test
  void 未知のパスには_index_html_を返し_api_配下には返さない(@TempDir Path dir) throws IOException {
    Files.writeString(dir.resolve("index.html"), "<html>teachme</html>");

    JavalinTest.test(
        WebApp.create(Optional.of(dir)),
        (server, client) -> {
          var page = client.get("/lessons/abc");
          assertEquals(200, page.code());
          assertTrue(page.body().string().contains("teachme"));

          assertEquals(404, client.get("/api/unknown").code());
        });
  }
}
