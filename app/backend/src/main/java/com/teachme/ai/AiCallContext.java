package com.teachme.ai;

import java.util.Objects;
import java.util.UUID;

/**
 * 外部 AI の呼び出し 1 回ごとの文脈 (アーキテクチャ設計 4.3)。
 *
 * <p>`ai.fake` は、利用者ごとに応答を決めて数えるために使い、`ai.real` は、ログに使う。
 *
 * @param userId 呼び出しを行った利用者の識別子
 * @param kind 呼び出しの種類
 */
public record AiCallContext(UUID userId, AiCallKind kind) {

  public AiCallContext {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(kind, "kind");
  }
}
