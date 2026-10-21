package com.teachme.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JsonTest {
  record Sample(String name, String memo, OffsetDateTime at) {}

  @Test
  void null_の項目は省き_日時は_ISO_8601_の文字列で書く() throws IOException {
    OffsetDateTime at = OffsetDateTime.of(2026, 10, 21, 18, 30, 0, 0, ZoneOffset.ofHours(9));

    String json = Json.mapper().writeValueAsString(new Sample("太郎", null, at));

    assertFalse(json.contains("memo"), json);
    assertEquals("{\"name\":\"太郎\",\"at\":\"2026-10-21T18:30:00+09:00\"}", json);
  }

  @Test
  void 知らない項目は_読み取りの失敗にしない() throws IOException {
    Sample sample = Json.mapper().readValue("{\"name\":\"太郎\",\"unknown\":1}", Sample.class);

    assertEquals("太郎", sample.name());
  }
}
