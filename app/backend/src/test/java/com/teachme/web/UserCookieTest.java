package com.teachme.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.testtools.JavalinTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserCookieTest {
  /** 解決された利用者の識別子を、そのまま返す。 */
  private static final Routes WHOAMI =
      app -> app.get("/api/whoami", ctx -> ctx.result(UserCookie.userId(ctx).toString()));

  @Test
  void 初めての利用者には_teachme_uid_を発行し_HttpOnly_と_SameSite_Lax_と_400_日を付ける() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(WHOAMI)),
        (server, client) -> {
          var response = client.get("/api/whoami");

          assertEquals(200, response.code());
          String setCookie = response.header("Set-Cookie");
          assertNotNull(setCookie);
          assertTrue(setCookie.startsWith("teachme_uid="), setCookie);
          assertTrue(setCookie.contains("HttpOnly"), setCookie);
          assertTrue(setCookie.contains("SameSite=Lax"), setCookie);
          assertTrue(setCookie.contains("Max-Age=34560000"), setCookie);
          assertTrue(setCookie.contains("Path=/"), setCookie);
          assertEquals(cookieValue(setCookie), response.body().string());
        });
  }

  @Test
  void 発行された_Cookie_を送り直すと_同じ利用者として扱い_発行し直さない() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(WHOAMI)),
        (server, client) -> {
          var first = client.get("/api/whoami");
          String cookie = "teachme_uid=" + cookieValue(first.header("Set-Cookie"));
          String firstId = first.body().string();

          var second = client.get("/api/whoami", request -> request.header("Cookie", cookie));

          assertEquals(firstId, second.body().string());
          assertNull(second.header("Set-Cookie"));
        });
  }

  @Test
  void 形式が不正な_Cookie_は無いものとして扱い_発行し直す() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(WHOAMI)),
        (server, client) -> {
          var response =
              client.get("/api/whoami", request -> request.header("Cookie", "teachme_uid=abc"));

          String issued = cookieValue(response.header("Set-Cookie"));
          assertNotEquals("abc", issued);
          assertEquals(issued, response.body().string());
        });
  }

  @Test
  void 利用者が違えば_別の識別子を発行する() {
    JavalinTest.test(
        WebApp.create(Optional.empty(), List.of(WHOAMI)),
        (server, client) -> {
          var first = client.get("/api/whoami");
          var second = client.get("/api/whoami");

          assertNotEquals(
              cookieValue(first.header("Set-Cookie")), cookieValue(second.header("Set-Cookie")));
        });
  }

  /** `teachme_uid=値; Max-Age=...` から値を取り出す。 */
  private static String cookieValue(String setCookie) {
    int start = "teachme_uid=".length();
    int end = setCookie.indexOf(';');
    return setCookie.substring(start, end < 0 ? setCookie.length() : end);
  }
}
