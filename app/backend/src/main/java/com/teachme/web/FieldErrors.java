package com.teachme.web;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 入力の検査で、項目ごとの誤りを集める。1 件でもあれば、`fields` を付けた 400 を返す。
 *
 * <p>項目の名前は、openapi.yaml の項目の名前 (lowerCamelCase) に合わせる。
 */
public final class FieldErrors {
  private final Map<String, String> fields = new LinkedHashMap<>();

  /** 同じ項目の誤りは、最初の文言を残す。 */
  public FieldErrors add(String field, String message) {
    fields.putIfAbsent(field, message);
    return this;
  }

  public boolean isEmpty() {
    return fields.isEmpty();
  }

  /**
   * 誤りが 1 件でもあれば、`code` と `message` の 400 を投げる。
   *
   * @throws ApiException 誤りがあるとき
   */
  public void throwIfAny(String code, String message) {
    if (!fields.isEmpty()) {
      throw new ApiException(400, code, message, Map.copyOf(fields));
    }
  }
}
