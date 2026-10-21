package com.teachme.web;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 例外と未知のパスを、`{ "error": { "code", "message", "fields?" } }` の形の応答に変える。 */
final class ApiErrors {
  static final String NOT_FOUND_MESSAGE = "対象が見つかりません";
  static final String INVALID_REQUEST_MESSAGE = "入力の形式が正しくありません";
  static final String INTERNAL_ERROR_MESSAGE = "サーバーで問題が起きました。もう一度お試しください";

  private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);

  private ApiErrors() {}

  static void register(Javalin app) {
    app.exception(
        ApiException.class,
        (e, ctx) ->
            ctx.status(e.status()).json(ErrorResponse.of(e.code(), e.getMessage(), e.fields())));
    app.exception(
        HttpResponseException.class,
        (e, ctx) -> {
          if (e.getStatus() == 404) {
            notFound(ctx);
          } else if (e.getStatus() < 500) {
            ctx.status(e.getStatus())
                .json(ErrorResponse.of("invalid_request", INVALID_REQUEST_MESSAGE, null));
          } else {
            internalError(ctx);
          }
        });
    app.exception(
        Exception.class,
        (e, ctx) -> {
          // 入力や応答の中身を含みうるため、ログには例外の種類だけを出す
          LOG.error("想定しない例外: {}", e.getClass().getName());
          internalError(ctx);
        });
  }

  static void notFound(Context ctx) {
    ctx.status(404).json(ErrorResponse.of("not_found", NOT_FOUND_MESSAGE, null));
  }

  private static void internalError(Context ctx) {
    ctx.status(500).json(ErrorResponse.of("internal_error", INTERNAL_ERROR_MESSAGE, null));
  }
}
