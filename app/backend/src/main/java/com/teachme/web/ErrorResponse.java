package com.teachme.web;

import java.util.Map;

/** エラーの応答の形 (openapi.yaml の ErrorResponse)。`fields` は、入力の誤りのときだけ付く。 */
public record ErrorResponse(Error error) {

  public record Error(String code, String message, Map<String, String> fields) {}

  public static ErrorResponse of(String code, String message, Map<String, String> fields) {
    return new ErrorResponse(new Error(code, message, fields));
  }
}
