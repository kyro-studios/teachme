package com.teachme.domain;

/**
 * AI 生徒の種類 (仕様書 1.3)。表示名、初期偏差値、目標偏差値、性格 (応答の方針)。
 *
 * <p>`id` は openapi.yaml の `StudentTypeId` と同じ値にする。
 */
public enum StudentType {
  INTUITIVE("intuitive", "直感・感覚派", 45, 50, "難しい専門用語に弱く、図解や身近な具体例を求める"),
  DILIGENT("diligent", "真面目な暗記派", 52, 58, "公式は覚えているが、なぜその解き方を使うのかという発想の過程を質問する"),
  LOGICAL("logical", "論理・ツッコミ派", 60, 65, "説明の飛躍や例外ケースについて、鋭く質問する");

  private final String id;
  private final String displayName;
  private final int initialDeviation;
  private final int targetDeviation;
  private final String personality;

  StudentType(
      String id,
      String displayName,
      int initialDeviation,
      int targetDeviation,
      String personality) {
    this.id = id;
    this.displayName = displayName;
    this.initialDeviation = initialDeviation;
    this.targetDeviation = targetDeviation;
    this.personality = personality;
  }

  public String id() {
    return id;
  }

  public String displayName() {
    return displayName;
  }

  public int initialDeviation() {
    return initialDeviation;
  }

  public int targetDeviation() {
    return targetDeviation;
  }

  /** 応答の方針。外部 AI への指示に使う。 */
  public String personality() {
    return personality;
  }
}
