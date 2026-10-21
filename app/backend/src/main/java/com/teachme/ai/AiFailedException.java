package com.teachme.ai;

/**
 * 外部 AI の呼び出しが失敗したこと。タイムアウト、応答の形の違反、HTTP の 4xx と 5xx を、すべてこの例外で表す。
 *
 * <p>自動の再試行はしない。例外の文言には、入力や応答の中身を含めない (ログに出すため)。
 */
public class AiFailedException extends RuntimeException {

  private final AiCallKind kind;

  public AiFailedException(AiCallKind kind) {
    super("外部 AI の呼び出しに失敗しました: " + kind.wireName());
    this.kind = kind;
  }

  public AiCallKind kind() {
    return kind;
  }
}
