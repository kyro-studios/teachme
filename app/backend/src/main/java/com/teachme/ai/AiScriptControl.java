package com.teachme.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * テストが、フェイクの外部 AI を操作するための口。`TestControlService` が使い、`ai.fake` が実装する。
 *
 * <p>すべての操作は、利用者ごとに効く。
 */
public interface AiScriptControl {

  /**
   * 利用者の `kind` の待ち列を置き換える。待ち列は、先頭から 1 つずつ使う。
   *
   * @param repeat true なら、最後の 1 つを使い切ったあとも返し続ける
   * @throws IllegalArgumentException 応答が空のとき、または `payload` の形が `kind` の形と合わないとき
   */
  void script(UUID userId, AiCallKind kind, List<ScriptedResponse> responses, boolean repeat);

  /** 利用者の、決めた応答と呼び出しの数えを、すべて消す。 */
  void reset(UUID userId);

  /** 利用者の、種類ごとの呼び出しの回数。呼ばれていない種類は 0 とする。 */
  Map<AiCallKind, Integer> callCounts(UUID userId);
}
