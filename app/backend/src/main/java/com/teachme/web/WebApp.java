package com.teachme.web;

import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.http.HandlerType;
import io.javalin.http.staticfiles.Location;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Javalin のアプリケーションを組み立てる。`/api` の API と、ビルド済みの画面の静的ファイルを同じオリジンで返す。 */
public final class WebApp {
  private static final String API_PREFIX = "/api";

  private WebApp() {}

  /**
   * @param staticDir 静的ファイルのディレクトリ。空なら静的ファイルを配信しない (開発)
   */
  public static Javalin create(Optional<Path> staticDir) {
    Javalin app =
        Javalin.create(
            config ->
                staticDir.ifPresent(
                    dir ->
                        config.staticFiles.add(
                            files -> {
                              files.hostedPath = "/";
                              files.directory = dir.toAbsolutePath().toString();
                              files.location = Location.EXTERNAL;
                            })));

    app.get(API_PREFIX + "/healthz", ctx -> ctx.contentType(ContentType.TEXT_PLAIN).result("ok"));

    staticDir.ifPresent(dir -> registerSpaFallback(app, dir.resolve("index.html")));
    return app;
  }

  /** 未知のパス (`/api` 以外) の GET に `index.html` を返す (SPA のフォールバック)。 */
  private static void registerSpaFallback(Javalin app, Path indexHtml) {
    app.error(
        404,
        ctx -> {
          String path = ctx.path();
          boolean isApi = path.equals(API_PREFIX) || path.startsWith(API_PREFIX + "/");
          if (ctx.method() != HandlerType.GET || isApi || !Files.isRegularFile(indexHtml)) {
            return;
          }
          try {
            ctx.status(200)
                .contentType(ContentType.TEXT_HTML)
                .result(Files.readString(indexHtml, StandardCharsets.UTF_8));
          } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
          }
        });
  }
}
