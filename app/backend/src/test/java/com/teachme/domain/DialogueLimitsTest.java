package com.teachme.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DialogueLimitsTest {
  private static final Instant START = Instant.parse("2026-10-21T01:00:00Z");

  @Test
  void 授業は_20_往復_600_秒_相談は_15_往復_600_秒() {
    assertEquals(20, DialogueLimits.lesson().maxRounds());
    assertEquals(Duration.ofSeconds(600), DialogueLimits.lesson().duration());
    assertEquals(15, DialogueLimits.consultation().maxRounds());
    assertEquals(Duration.ofSeconds(600), DialogueLimits.consultation().duration());
  }

  @Test
  void 時間の上書きは_往復の上限を変えない() {
    DialogueLimits limits = DialogueLimits.lesson().withSeconds(5);

    assertEquals(20, limits.maxRounds());
    assertEquals(Duration.ofSeconds(5), limits.duration());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 601})
  void 上書きの秒数が_1_から_600_の外なら_IllegalArgumentException(int seconds) {
    assertThrows(
        IllegalArgumentException.class, () -> DialogueLimits.lesson().withSeconds(seconds));
  }

  @Test
  void 期限は開始から_時間の分だけ後() {
    assertEquals(START.plusSeconds(600), DialogueLimits.lesson().deadlineAt(START));
  }

  @Test
  void 往復が上限に達すると_受け付けない() {
    DialogueLimits limits = DialogueLimits.lesson();
    Instant deadline = limits.deadlineAt(START);

    assertTrue(limits.canAccept(19, deadline, START));
    assertFalse(limits.canAccept(20, deadline, START));
  }

  @Test
  void 期限の時刻ちょうどから_受け付けない() {
    DialogueLimits limits = DialogueLimits.consultation().withSeconds(10);
    Instant deadline = limits.deadlineAt(START);

    assertTrue(limits.canAccept(0, deadline, START.plusSeconds(9)));
    assertTrue(limits.isDeadlineReached(deadline, deadline));
    assertFalse(limits.canAccept(0, deadline, deadline));
    assertFalse(limits.isDeadlineReached(deadline, deadline.minusMillis(1)));
  }
}
