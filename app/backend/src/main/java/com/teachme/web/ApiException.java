package com.teachme.web;

import java.util.Map;

/**
 * API の誤り。投げると、{@link ApiErrors} が `{ "error": { "code", "message", "fields?" } }` の形の応答に変える。
 *
 * <p>`message` は画面に出してよい日本語の文言にする (openapi.yaml の共通の決まり)。
 */
public final class ApiException extends RuntimeException {
  private final int status;
  private final String code;
  private final Map<String, String> fields;

  /**
   * @param fields 入力の誤りの項目ごとの文言。無ければ null
   */
  public ApiException(int status, String code, String message, Map<String, String> fields) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = fields;
  }

  public static ApiException badRequest(String code, String message) {
    return new ApiException(400, code, message, null);
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  /** 項目の誤りが無ければ null。 */
  public Map<String, String> fields() {
    return fields;
  }
}
