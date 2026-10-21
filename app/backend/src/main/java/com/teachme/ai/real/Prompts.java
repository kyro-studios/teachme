package com.teachme.ai.real;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指示 (プロンプト) のリソース (`prompts/*.md`)。すべての呼び出しに、共通の決まり (`common.md`) を付ける。
 *
 * <p>差し込みの位置は `{{名前}}` で書く。差し込む値は、AI
 * 生徒の性格や得点の水準などの、利用者の入力ではないものだけに限る。利用者の発話や字幕は、指示には入れず、利用者の文章として送る。
 */
final class Prompts {

  private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

  private Prompts() {}

  /**
   * 共通の決まりと、呼び出しの指示を続けた、指示の文章を作る。
   *
   * @param name 指示のファイルの名前 (拡張子を除く)
   * @param variables `{{名前}}` の位置に入れる値
   */
  static String system(String name, Map<String, String> variables) {
    String text = fragment("common") + "\n\n" + fragment(name);
    for (Map.Entry<String, String> variable : variables.entrySet()) {
      text = text.replace("{{" + variable.getKey() + "}}", variable.getValue());
    }
    return text;
  }

  /**
   * @throws IllegalStateException 資源が無いとき (実装の誤り)
   */
  static String fragment(String name) {
    return CACHE.computeIfAbsent(name, Prompts::read);
  }

  private static String read(String name) {
    String path = "/prompts/" + name + ".md";
    try (InputStream in = Prompts.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("指示の資源が見つかりません: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("指示の資源を読めませんでした: " + path, e);
    }
  }
}
