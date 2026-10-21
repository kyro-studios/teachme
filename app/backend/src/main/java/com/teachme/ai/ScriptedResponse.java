package com.teachme.ai;

import java.util.Map;

/**
 * テストが決める、外部 AI の次の応答 (openapi.yaml の `AiScript` の 1 件)。
 *
 * <p>`payload` は成功のときの中身で、`kind` ごとの形 (openapi.yaml の決まり)。`null` なら既定の成功の応答を使う。失敗のときは無視する。
 *
 * @param success 成功なら true、失敗なら false
 * @param payload 成功のときの中身。JSON から読んだままの形
 */
public record ScriptedResponse(boolean success, Map<String, Object> payload) {}
