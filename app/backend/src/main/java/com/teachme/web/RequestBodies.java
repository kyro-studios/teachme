package com.teachme.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;

/** リクエストの本文の読み取り。形式が不正なときは、400 の {@link ApiException} にする。 */
public final class RequestBodies {
  private RequestBodies() {}

  /**
   * @throws ApiException 本文が空、または JSON として読めないとき
   */
  public static <T> T readJson(Context ctx, Class<T> type) {
    try {
      return Json.mapper().readValue(ctx.body(), type);
    } catch (JsonProcessingException e) {
      // Jackson の例外の文言には本文の一部が入りうるため、使わない
      throw ApiException.badRequest("invalid_request", ApiErrors.INVALID_REQUEST_MESSAGE);
    }
  }
}
