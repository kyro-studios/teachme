package com.teachme.application;

import java.util.Map;

/**
 * 入力の誤り (400 の `validation_error`)。項目の名前から、画面に出す文言への対応を持つ。
 *
 * <p>何も保存しない。HTTP の状態への変換は `web` が行う。
 */
public final class InvalidInputException extends RuntimeException {

  private final Map<String, String> fields;

  /**
   * @param fields 項目の名前から文言への対応。1 件以上
   */
  public InvalidInputException(Map<String, String> fields) {
    super("入力の内容に誤りがあります");
    this.fields = Map.copyOf(fields);
  }

  public Map<String, String> fields() {
    return fields;
  }
}
