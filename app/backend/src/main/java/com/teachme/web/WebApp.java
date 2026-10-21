package com.teachme.web;

import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Javalin のアプリケーションを組み立てる。`/api` の API と、ビルド済みの画面の静的ファイルを同じオリジンで返す。 */
public final class WebApp {
  private static final String API_PREFIX = "/api";

  /** リクエスト全体の上限 (openapi.yaml の共通の決まり。画像 1 枚 10 MB に、ほかの項目の分を足す)。 */
  private static final long MAX_REQUEST_BYTES = 32L * 1024 * 1024;

  private WebApp() {}

  public static Javalin create(Optional<Path> staticDir) {
    return create(staticDir, List.of());
  }

  /**
   * @param staticDir 静的ファイルのディレクトリ。空なら静的ファイルを配信しない (開発)
   * @param routes API ごとのルート
   */
  public static Javalin create(Optional<Path> staticDir, List<Routes> routes) {
    Javalin app =
        Javalin.create(
            config -> {
              config.jsonMapper(new JavalinJackson(Json.mapper(), false));
              config.http.maxRequestSize = MAX_REQUEST_BYTES;
              staticDir.ifPresent(
                  dir ->
                      config.staticFiles.add(
                          files -> {
                            files.hostedPath = "/";
                            files.directory = dir.toAbsolutePath().toString();
                            files.location = Location.EXTERNAL;
                          }));
            });

    app.before(API_PREFIX + "/*", UserCookie::resolve);
    ApiErrors.register(app);
    app.get(API_PREFIX + "/healthz", ctx -> ctx.contentType(ContentType.TEXT_PLAIN).result("ok"));
    routes.forEach(route -> route.register(app));
    app.error(404, ctx -> handleNotFound(ctx, staticDir));
    return app;
  }

  /** 未知の `/api` のパスは、JSON の 404 を返す。それ以外の GET は `index.html` を返す (SPA のフォールバック)。 */
  private static void handleNotFound(Context ctx, Optional<Path> staticDir) {
    String path = ctx.path();
    if (path.equals(API_PREFIX) || path.startsWith(API_PREFIX + "/")) {
      ApiErrors.notFound(ctx);
      return;
    }
    Optional<Path> indexHtml =
        staticDir.map(dir -> dir.resolve("index.html")).filter(Files::isRegularFile);
    if (ctx.method() != HandlerType.GET || indexHtml.isEmpty()) {
      return;
    }
    try {
      ctx.status(200)
          .contentType(ContentType.TEXT_HTML)
          .result(Files.readString(indexHtml.get(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
