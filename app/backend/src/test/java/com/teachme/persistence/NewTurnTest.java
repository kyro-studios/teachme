package com.teachme.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teachme.domain.BoardWrite;
import com.teachme.domain.OrganizedProblem;
import com.teachme.domain.Speaker;
import java.util.List;
import org.junit.jupiter.api.Test;

class NewTurnTest {

  @Test
  void 先輩先生の字幕は_表情を持たず_ボードへの書き込みを_JSON_の追加情報として持つ() throws Exception {
    DialogueRepository.NewTurn turn =
        DialogueRepository.NewTurn.mentor(
            "判別式を使います",
            List.of(
                new BoardWrite(BoardWrite.Kind.TEXT, "D = b^2 - 4ac", null),
                new BoardWrite(
                    BoardWrite.Kind.HIGHLIGHT,
                    null,
                    new OrganizedProblem.Region(0.1, 0.2, 0.3, 0.1))));

    assertEquals(Speaker.MENTOR, turn.speaker());
    assertEquals("判別式を使います", turn.body());
    assertNull(turn.expression());
    JsonNode writes = new ObjectMapper().readTree(turn.extras());
    assertEquals(2, writes.size());
    assertEquals("text", writes.get(0).get("kind").asText());
    assertEquals("D = b^2 - 4ac", writes.get(0).get("text").asText());
    assertEquals("highlight", writes.get(1).get("kind").asText());
    assertEquals(0.3, writes.get(1).get("region").get("width").asDouble());
  }

  @Test
  void 書き込みが無ければ_追加情報は無い() {
    DialogueRepository.NewTurn turn = DialogueRepository.NewTurn.mentor("よく考えてみましょう", List.of());

    assertNull(turn.extras());
  }
}
