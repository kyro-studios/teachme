package com.teachme.ai.real;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JsonSchemaCheckTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static JsonNode json(String text) throws Exception {
    return MAPPER.readTree(text);
  }

  private static boolean matches(String schema, String value) throws Exception {
    return JsonSchemaCheck.matches(json(schema), json(value));
  }

  private static final String OBJECT_SCHEMA =
      """
      {"type":"object","additionalProperties":false,"required":["name","tags"],
       "properties":{
         "name":{"type":"string"},
         "tags":{"type":"array","minItems":1,"maxItems":2,"items":{"type":"string"}},
         "score":{"type":"integer","minimum":0,"maximum":2}}}
      """;

  @Test
  void 必須の項目と_型と_配列の個数を_検査する() throws Exception {
    assertTrue(matches(OBJECT_SCHEMA, "{\"name\":\"a\",\"tags\":[\"x\"]}"));
    assertFalse(matches(OBJECT_SCHEMA, "{\"name\":\"a\"}"));
    assertFalse(matches(OBJECT_SCHEMA, "{\"name\":1,\"tags\":[\"x\"]}"));
    assertFalse(matches(OBJECT_SCHEMA, "{\"name\":\"a\",\"tags\":[]}"));
    assertFalse(matches(OBJECT_SCHEMA, "{\"name\":\"a\",\"tags\":[\"x\",\"y\",\"z\"]}"));
  }

  @Test
  void 範囲と整数を_検査する() throws Exception {
    assertTrue(matches(OBJECT_SCHEMA, "{\"name\":\"a\",\"tags\":[\"x\"],\"score\":2}"));
    assertFalse(matches(OBJECT_SCHEMA, "{\"name\":\"a\",\"tags\":[\"x\"],\"score\":3}"));
    assertFalse(matches(OBJECT_SCHEMA, "{\"name\":\"a\",\"tags\":[\"x\"],\"score\":1.5}"));
  }

  @Test
  void 余分な項目は_additionalProperties_が_false_のとき失敗する() throws Exception {
    assertFalse(matches(OBJECT_SCHEMA, "{\"name\":\"a\",\"tags\":[\"x\"],\"extra\":1}"));
  }

  @Test
  void enum_と_anyOf_の_null_を_検査する() throws Exception {
    String schema =
        """
        {"type":"object","additionalProperties":false,"required":["kind","region"],
         "properties":{
           "kind":{"type":"string","enum":["point","question_mark"]},
           "region":{"anyOf":[{"type":"null"},{"type":"object","additionalProperties":false,
             "required":["x"],"properties":{"x":{"type":"number","minimum":0,"maximum":1}}}]}}}
        """;

    assertTrue(matches(schema, "{\"kind\":\"point\",\"region\":null}"));
    assertTrue(matches(schema, "{\"kind\":\"question_mark\",\"region\":{\"x\":0.5}}"));
    assertFalse(matches(schema, "{\"kind\":\"other\",\"region\":null}"));
    assertFalse(matches(schema, "{\"kind\":\"point\",\"region\":{\"x\":2}}"));
    assertFalse(matches(schema, "{\"kind\":\"point\",\"region\":\"x\"}"));
  }

  @Test
  void type_の配列で_null_を許す() throws Exception {
    String schema =
        "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"text\"],\"properties\":{\"text\":{\"type\":[\"string\",\"null\"]}}}";

    assertTrue(matches(schema, "{\"text\":null}"));
    assertTrue(matches(schema, "{\"text\":\"x\"}"));
    assertFalse(matches(schema, "{\"text\":1}"));
  }

  @Test
  void 空の本文は_オブジェクトとして合わない() throws Exception {
    assertFalse(JsonSchemaCheck.matches(json(OBJECT_SCHEMA), MAPPER.readTree("")));
  }
}
