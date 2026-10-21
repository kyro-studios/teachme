package com.teachme.ai.real;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** `schemas/*.json` と `prompts/*.md` の資源が、読めて、決まりに合うことを確かめる。 */
class ResourcesTest {

  /** 応答の種類ごとの名前。スキーマ (`schemas/<名前>.json`) と指示 (`prompts/<名前>.md`) の両方に使う。 */
  private static final List<String> NAMES =
      List.of(
          "organize",
          "student-reply",
          "evaluation",
          "mentor-reply",
          "summary",
          "mock-exam",
          "homework");

  /**
   * OpenAI の Structured Outputs の決まり: すべての object で、項目を全部 required にし、additionalProperties を false
   * にする。
   */
  private static void assertStrictObjects(JsonNode schema, String path) {
    if (schema.path("type").isTextual() && schema.get("type").asText().equals("object")) {
      assertTrue(
          schema.path("additionalProperties").isBoolean()
              && !schema.get("additionalProperties").asBoolean(),
          path + " は additionalProperties を false にしてください");
      JsonNode properties = schema.path("properties");
      for (Iterator<String> names = properties.fieldNames(); names.hasNext(); ) {
        String name = names.next();
        boolean required = false;
        for (JsonNode item : schema.path("required")) {
          required |= item.asText().equals(name);
        }
        assertTrue(required, path + "/" + name + " を required に入れてください");
      }
    }
    for (Map.Entry<String, JsonNode> child : schema.properties()) {
      assertStrictObjects(child.getValue(), path + "/" + child.getKey());
    }
    if (schema.has("items")) {
      assertStrictObjects(schema.get("items"), path + "/items");
    }
    if (schema.has("anyOf")) {
      int index = 0;
      for (JsonNode option : schema.get("anyOf")) {
        assertStrictObjects(option, path + "/anyOf/" + index++);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "organize",
        "student-reply",
        "evaluation",
        "mentor-reply",
        "summary",
        "mock-exam",
        "homework"
      })
  void スキーマは_strict_の決まりに合う(String name) {
    assertStrictObjects(Schemas.load(name), name);
  }

  @Test
  void 指示は_すべて読め_差し込みを_すべて埋めると_残りの差し込みが無い() {
    for (String name : NAMES) {
      String text =
          Prompts.system(
              name,
              Map.of(
                  "displayName", "名前",
                  "personality", "性格",
                  "levelRules", "水準"));
      assertFalse(text.contains("{{"), name + " に、埋めていない差し込みがあります");
    }
    assertTrue(Prompts.fragment("mock-exam-high").contains("80 点以上"));
    assertTrue(Prompts.fragment("mock-exam-middle").contains("50 点以上"));
    assertTrue(Prompts.fragment("mock-exam-low").contains("49 点以下"));
  }
}
