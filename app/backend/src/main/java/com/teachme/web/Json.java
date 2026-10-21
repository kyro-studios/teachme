package com.teachme.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** JSON の読み書きの設定 (Jackson)。日時は ISO 8601 の文字列で書き、null の項目は省く (`fields?` など)。 */
public final class Json {
  // 設定を済ませた後の ObjectMapper は、スレッドから安全に使える。Javalin と本文の読み取りで共有する
  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

  private Json() {}

  public static ObjectMapper mapper() {
    return MAPPER;
  }
}
