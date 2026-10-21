package com.teachme.ai.real;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM の応答の JSON スキーマ (`schemas/<名前>.json`)。リクエストに付け、受け取った応答の検査にも使う。
 *
 * <p>OpenAI の Structured Outputs の決まりに合わせ、すべての項目を `required` に入れ、`additionalProperties` を false
 * にする。
 */
final class Schemas {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Map<String, JsonNode> CACHE = new ConcurrentHashMap<>();

  private Schemas() {}

  /**
   * @throws IllegalStateException 資源が無いとき (実装の誤り)
   */
  static JsonNode load(String name) {
    return CACHE.computeIfAbsent(name, Schemas::read);
  }

  private static JsonNode read(String name) {
    String path = "/schemas/" + name + ".json";
    try (InputStream in = Schemas.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("スキーマの資源が見つかりません: " + path);
      }
      return MAPPER.readTree(in);
    } catch (IOException e) {
      throw new IllegalStateException("スキーマの資源を読めませんでした: " + path, e);
    }
  }
}
