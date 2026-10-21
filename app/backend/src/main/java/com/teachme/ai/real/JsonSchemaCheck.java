package com.teachme.ai.real;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Map;

/**
 * JSON スキーマのうち、応答の形の検査に使う部分だけを行う。
 *
 * <p>扱うキーワードは、`type`、`enum`、`properties`、`required`、`additionalProperties` (false
 * のとき)、`items`、`minItems`、`maxItems`、`minimum`、`maximum`、`anyOf` の 11
 * 個とする。検証のライブラリを増やさないため、リソースのスキーマはこの範囲に限って書く。
 */
final class JsonSchemaCheck {

  private JsonSchemaCheck() {}

  /**
   * @return 値がスキーマに合うなら true
   * @throws IllegalArgumentException スキーマに、扱わない `type` があるとき (実装の誤り)
   */
  static boolean matches(JsonNode schema, JsonNode value) {
    if (schema.has("anyOf") && !anyMatches(schema.get("anyOf"), value)) {
      return false;
    }
    if (schema.has("type") && !typeMatches(schema.get("type"), value)) {
      return false;
    }
    if (schema.has("enum") && !contains(schema.get("enum"), value)) {
      return false;
    }
    if (value.isNumber() && !withinRange(schema, value)) {
      return false;
    }
    if (value.isArray() && !arrayMatches(schema, value)) {
      return false;
    }
    return !value.isObject() || objectMatches(schema, value);
  }

  private static boolean anyMatches(JsonNode options, JsonNode value) {
    for (JsonNode option : options) {
      if (matches(option, value)) {
        return true;
      }
    }
    return false;
  }

  private static boolean typeMatches(JsonNode type, JsonNode value) {
    if (type.isArray()) {
      for (JsonNode candidate : type) {
        if (typeMatches(candidate, value)) {
          return true;
        }
      }
      return false;
    }
    return switch (type.asText()) {
      case "object" -> value.isObject();
      case "array" -> value.isArray();
      case "string" -> value.isTextual();
      case "integer" -> value.isIntegralNumber();
      case "number" -> value.isNumber();
      case "boolean" -> value.isBoolean();
      case "null" -> value.isNull();
      default -> throw new IllegalArgumentException("扱わない type です: " + type.asText());
    };
  }

  private static boolean contains(JsonNode values, JsonNode value) {
    for (JsonNode candidate : values) {
      if (candidate.equals(value)) {
        return true;
      }
    }
    return false;
  }

  private static boolean withinRange(JsonNode schema, JsonNode value) {
    double number = value.doubleValue();
    if (schema.has("minimum") && number < schema.get("minimum").doubleValue()) {
      return false;
    }
    return !schema.has("maximum") || number <= schema.get("maximum").doubleValue();
  }

  private static boolean arrayMatches(JsonNode schema, JsonNode value) {
    if (schema.has("minItems") && value.size() < schema.get("minItems").asInt()) {
      return false;
    }
    if (schema.has("maxItems") && value.size() > schema.get("maxItems").asInt()) {
      return false;
    }
    if (schema.has("items")) {
      for (JsonNode item : value) {
        if (!matches(schema.get("items"), item)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean objectMatches(JsonNode schema, JsonNode value) {
    JsonNode properties = schema.path("properties");
    for (JsonNode name : schema.path("required")) {
      if (!value.has(name.asText())) {
        return false;
      }
    }
    boolean closed =
        schema.path("additionalProperties").isBoolean()
            && !schema.get("additionalProperties").asBoolean();
    if (closed) {
      Iterator<String> names = value.fieldNames();
      while (names.hasNext()) {
        if (!properties.has(names.next())) {
          return false;
        }
      }
    }
    for (Map.Entry<String, JsonNode> property : properties.properties()) {
      if (value.has(property.getKey())
          && !matches(property.getValue(), value.get(property.getKey()))) {
        return false;
      }
    }
    return true;
  }
}
