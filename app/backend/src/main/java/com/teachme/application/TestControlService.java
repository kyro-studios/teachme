package com.teachme.application;

import com.teachme.ai.AiCallKind;
import com.teachme.ai.AiScriptControl;
import com.teachme.ai.ScriptedResponse;
import com.teachme.domain.DialogueLimits;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * テスト用の操作 (`/api/test/*`)。フェイクの外部 AI の応答と呼び出しの数え、授業と相談の時間の上限を、呼んだ利用者の分だけ決める。
 *
 * <p>`AI_MODE=fake` のときだけ、bootstrap が組み立てて `web` に登録する。
 */
public final class TestControlService {

  /** 応答 1 件の指定。`payload` は、`kind` ごとの形 (openapi.yaml の `AiScript`)。 */
  public record ScriptedAttempt(boolean success, Map<String, Object> payload) {}

  private final AiScriptControl aiScript;
  private final Map<UUID, DialogueLimits> lessonOverrides = new ConcurrentHashMap<>();
  private final Map<UUID, DialogueLimits> consultationOverrides = new ConcurrentHashMap<>();

  public TestControlService(AiScriptControl aiScript) {
    this.aiScript = Objects.requireNonNull(aiScript, "aiScript");
  }

  /**
   * 利用者の、外部 AI の `kind` の応答の待ち列を置き換える。
   *
   * @param kind openapi.yaml の `AiScript` の `kind` (例: `student_reply`)
   * @param attempts 先頭から 1 つずつ使う応答。1 件以上
   * @throws IllegalArgumentException 知らない `kind`、空の応答、`payload` の形の誤り
   */
  public void scriptAi(UUID userId, String kind, boolean repeat, List<ScriptedAttempt> attempts) {
    AiCallKind callKind = AiCallKind.fromWireName(kind);
    List<ScriptedResponse> responses =
        attempts.stream()
            .map(attempt -> new ScriptedResponse(attempt.success(), attempt.payload()))
            .toList();
    aiScript.script(userId, callKind, responses, repeat);
  }

  /** 利用者の、決めた応答と呼び出しの数えを、すべて消す。 */
  public void resetAi(UUID userId) {
    aiScript.reset(userId);
  }

  /** 利用者の、種類ごとの呼び出しの回数。キーは openapi.yaml の `kind` の名前で、呼ばれていない種類は 0 とする。 */
  public Map<String, Integer> aiCalls(UUID userId) {
    Map<AiCallKind, Integer> calls = aiScript.callCounts(userId);
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (AiCallKind kind : AiCallKind.values()) {
      counts.put(kind.wireName(), calls.getOrDefault(kind, 0));
    }
    return counts;
  }

  /**
   * 授業と相談の時間の上限を、利用者の分だけ決める。`null` の項目は既定の 600 秒に戻す。
   *
   * <p>値は、このあと始める授業と相談から効く。往復の上限は変わらない。
   *
   * @throws IllegalArgumentException 秒数が 1 から 600 の範囲の外のとき
   */
  public void setLimits(UUID userId, Integer lessonSeconds, Integer consultationSeconds) {
    DialogueLimits lesson =
        lessonSeconds == null ? null : DialogueLimits.lesson().withSeconds(lessonSeconds);
    DialogueLimits consultation =
        consultationSeconds == null
            ? null
            : DialogueLimits.consultation().withSeconds(consultationSeconds);
    store(lessonOverrides, userId, lesson);
    store(consultationOverrides, userId, consultation);
  }

  /** 利用者の授業の上限。上書きが無ければ既定。 */
  public DialogueLimits lessonLimits(UUID userId) {
    return lessonOverrides.getOrDefault(userId, DialogueLimits.lesson());
  }

  /** 利用者の相談の上限。上書きが無ければ既定。 */
  public DialogueLimits consultationLimits(UUID userId) {
    return consultationOverrides.getOrDefault(userId, DialogueLimits.consultation());
  }

  private static void store(
      Map<UUID, DialogueLimits> overrides, UUID userId, DialogueLimits limits) {
    if (limits == null) {
      overrides.remove(userId);
    } else {
      overrides.put(userId, limits);
    }
  }
}
