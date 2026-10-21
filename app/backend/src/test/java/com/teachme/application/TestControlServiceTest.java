package com.teachme.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.teachme.ai.AiCallKind;
import com.teachme.ai.AiScriptControl;
import com.teachme.ai.ScriptedResponse;
import com.teachme.application.TestControlService.ScriptedAttempt;
import com.teachme.domain.DialogueLimits;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TestControlServiceTest {
  private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

  /** 呼び出しを覚えるだけの、AiScriptControl の代わり。 */
  private static final class RecordingScript implements AiScriptControl {
    AiCallKind scriptedKind;
    List<ScriptedResponse> scriptedResponses;
    boolean scriptedRepeat;
    UUID resetUser;
    final Map<AiCallKind, Integer> counts = new EnumMap<>(AiCallKind.class);

    @Override
    public void script(
        UUID userId, AiCallKind kind, List<ScriptedResponse> responses, boolean repeat) {
      scriptedKind = kind;
      scriptedResponses = responses;
      scriptedRepeat = repeat;
    }

    @Override
    public void reset(UUID userId) {
      resetUser = userId;
    }

    @Override
    public Map<AiCallKind, Integer> callCounts(UUID userId) {
      return counts;
    }
  }

  private final RecordingScript script = new RecordingScript();
  private final TestControlService service = new TestControlService(script);

  @Test
  void scriptAi_は_kind_を_AiCallKind_にして_応答の待ち列を渡す() {
    service.scriptAi(USER, "student_reply", true, List.of(new ScriptedAttempt(false, null)));

    assertEquals(AiCallKind.STUDENT_REPLY, script.scriptedKind);
    assertTrue(script.scriptedRepeat);
    assertEquals(1, script.scriptedResponses.size());
    assertFalse(script.scriptedResponses.get(0).success());
  }

  @Test
  void scriptAi_は_知らない_kind_を_IllegalArgumentException_にする() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.scriptAi(USER, "unknown", false, List.of(new ScriptedAttempt(true, null))));
  }

  @Test
  void resetAi_は_利用者を指定して_委譲する() {
    service.resetAi(USER);

    assertEquals(USER, script.resetUser);
  }

  @Test
  void aiCalls_は_kind_の名前で_全種類を_0_を含めて返す() {
    script.counts.put(AiCallKind.STT, 2);

    Map<String, Integer> counts = service.aiCalls(USER);

    assertEquals(AiCallKind.values().length, counts.size());
    assertEquals(2, counts.get("stt"));
    assertEquals(0, counts.get("organize"));
    assertEquals(0, counts.get("tts"));
  }

  @Test
  void 時間の上書きは_呼んだ利用者だけに効き_往復の上限は変えない() {
    service.setLimits(USER, 5, 7);

    assertEquals(Duration.ofSeconds(5), service.lessonLimits(USER).duration());
    assertEquals(20, service.lessonLimits(USER).maxRounds());
    assertEquals(Duration.ofSeconds(7), service.consultationLimits(USER).duration());
    assertEquals(15, service.consultationLimits(USER).maxRounds());
    assertEquals(DialogueLimits.lesson(), service.lessonLimits(OTHER));
    assertEquals(DialogueLimits.consultation(), service.consultationLimits(OTHER));
  }

  @Test
  void 時間の指定が_null_なら_既定に戻す() {
    service.setLimits(USER, 5, 5);

    service.setLimits(USER, null, null);

    assertEquals(DialogueLimits.lesson(), service.lessonLimits(USER));
    assertEquals(DialogueLimits.consultation(), service.consultationLimits(USER));
  }

  @Test
  void 範囲の外の秒数があると_どちらも変えない() {
    service.setLimits(USER, 5, null);

    assertThrows(IllegalArgumentException.class, () -> service.setLimits(USER, 3, 0));

    assertEquals(Duration.ofSeconds(5), service.lessonLimits(USER).duration());
  }
}
