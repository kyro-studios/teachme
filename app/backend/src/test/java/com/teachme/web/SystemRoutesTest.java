package com.teachme.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.javalin.testtools.JavalinTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SystemRoutesTest {
  @Test
  void config_は_aiMode_を返す() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(new SystemRoutes("fake"))),
        (server, client) -> {
          var response = client.get("/api/config");

          assertEquals(200, response.code());
          assertEquals("{\"aiMode\":\"fake\"}", response.body().string());
        });
  }

  @Test
  void aiMode_が_real_のときも_real_を返す() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(new SystemRoutes("real"))),
        (server, client) -> {
          var response = client.get("/api/config");

          assertEquals("{\"aiMode\":\"real\"}", response.body().string());
        });
  }
}
