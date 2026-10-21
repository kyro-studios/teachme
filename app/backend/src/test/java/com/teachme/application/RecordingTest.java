package com.teachme.application;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.teachme.application.DialogueService.Recording;
import org.junit.jupiter.api.Test;

class RecordingTest {

  @Test
  void 空の録音は受け付けない() {
    assertThrows(IllegalArgumentException.class, () -> new Recording("audio/webm", new byte[0]));
  }

  @Test
  void 五メガバイトを超える録音は受け付けない() {
    byte[] tooLarge = new byte[(int) Recording.MAX_BYTES + 1];

    assertThrows(IllegalArgumentException.class, () -> new Recording("audio/webm", tooLarge));
  }

  @Test
  void 五メガバイトちょうどの録音は受け付ける() {
    byte[] max = new byte[(int) Recording.MAX_BYTES];

    new Recording("audio/webm", max);
  }
}
