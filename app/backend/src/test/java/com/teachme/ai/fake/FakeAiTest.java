package com.teachme.ai.fake;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.teachme.ai.AiCallContext;
import com.teachme.ai.AiCallKind;
import com.teachme.ai.AiFailedException;
import com.teachme.ai.LanguageModel.AnnotationKind;
import com.teachme.ai.LanguageModel.EvaluationInput;
import com.teachme.ai.LanguageModel.StudentExpression;
import com.teachme.ai.LanguageModel.StudentReply;
import com.teachme.ai.LanguageModel.StudentReplyInput;
import com.teachme.ai.LanguageModel.SummaryInput;
import com.teachme.ai.Media;
import com.teachme.ai.ScriptedResponse;
import com.teachme.ai.TextToSpeech.Voice;
import com.teachme.domain.StudentType;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakeAiTest {
  private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private final FakeAi ai = new FakeAi();

  private static ScriptedResponse success(Map<String, Object> payload) {
    return new ScriptedResponse(true, payload);
  }

  private static ScriptedResponse failure() {
    return new ScriptedResponse(false, null);
  }

  /** null の値を持てる Map を作る (Map.of は null を受け付けない)。 */
  private static Map<String, Object> payload(Object... keysAndValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return map;
  }

  private static AiCallContext context(UUID userId, AiCallKind kind) {
    return new AiCallContext(userId, kind);
  }

  private static Media audio() {
    return new Media("audio/webm", new byte[] {1, 2, 3});
  }

  private static StudentReplyInput studentReplyInput() {
    return new StudentReplyInput(
        StudentType.INTUITIVE, "問題文", "途中の解答", audio(), "発話", List.of(), List.of());
  }

  private static EvaluationInput evaluationInput() {
    return new EvaluationInput(
        List.of("a", "b", "c"), "答え", List.of("解説"), "問題文", audio(), "発話", List.of(), List.of());
  }

  private static SummaryInput summaryInput() {
    return new SummaryInput(StudentType.DILIGENT, List.of(), audio());
  }

  private StudentReply reply(UUID userId) {
    return ai.studentReply(context(userId, AiCallKind.STUDENT_REPLY), studentReplyInput());
  }

  @Test
  void 何も決めていないとき_すべて成功の既定の応答を返す() {
    StudentReply reply = reply(USER);

    assertEquals("なぜそうなるのですか？", reply.text());
    assertEquals(StudentExpression.TROUBLED, reply.expression());
    assertNull(reply.annotation());
    assertEquals("ここの式変形が分かりません", ai.transcribe(context(USER, AiCallKind.STT), audio()));
    assertEquals(
        "audio/mpeg",
        ai.synthesize(context(USER, AiCallKind.TTS), new Voice.Mentor(), "やあ").mimeType());
  }

  @Test
  void 待ち列は先頭から_1_つずつ使い_使い切ったら既定に戻る() {
    ai.script(
        USER,
        AiCallKind.STUDENT_REPLY,
        List.of(
            success(payload("text", "一つ目", "expression", "normal", "annotation", null)),
            success(payload("text", "二つ目", "expression", "convinced", "annotation", null))),
        false);

    assertEquals("一つ目", reply(USER).text());
    assertEquals(StudentExpression.CONVINCED, reply(USER).expression());
    assertEquals("なぜそうなるのですか？", reply(USER).text());
  }

  @Test
  void 応答の指定の中の_annotation_で_指し示しの印を返せる() {
    ai.script(
        USER,
        AiCallKind.STUDENT_REPLY,
        List.of(
            success(
                payload(
                    "text",
                    "ここですか？",
                    "expression",
                    "troubled",
                    "annotation",
                    payload(
                        "kind",
                        "question_mark",
                        "region",
                        payload("x", 0.1, "y", 0.2, "width", 0.3, "height", 0.4))))),
        false);

    StudentReply reply = reply(USER);

    assertEquals(AnnotationKind.QUESTION_MARK, reply.annotation().kind());
    assertEquals(0.3, reply.annotation().region().width());
  }

  @Test
  void 失敗の応答は_AiFailedException_を投げ_呼び出しは数える() {
    ai.script(USER, AiCallKind.STUDENT_REPLY, List.of(failure()), false);

    AiFailedException error = assertThrows(AiFailedException.class, () -> reply(USER));

    assertEquals(AiCallKind.STUDENT_REPLY, error.kind());
    assertEquals("なぜそうなるのですか？", reply(USER).text());
    assertEquals(2, ai.callCounts(USER).get(AiCallKind.STUDENT_REPLY));
  }

  @Test
  void repeat_が_true_なら_最後の応答を使い_続ける() {
    ai.script(
        USER,
        AiCallKind.EVALUATION,
        List.of(
            success(payload("degrees", List.of(2, 2, 2))),
            success(payload("degrees", List.of(0, 0, 0)))),
        true);

    assertEquals(
        List.of(2, 2, 2),
        ai.evaluate(context(USER, AiCallKind.EVALUATION), evaluationInput()).degrees());
    assertEquals(
        List.of(0, 0, 0),
        ai.evaluate(context(USER, AiCallKind.EVALUATION), evaluationInput()).degrees());
    assertEquals(
        List.of(0, 0, 0),
        ai.evaluate(context(USER, AiCallKind.EVALUATION), evaluationInput()).degrees());
  }

  @Test
  void 待ち列と数えは利用者ごとに隔離される() {
    ai.script(
        USER,
        AiCallKind.SUMMARY,
        List.of(success(payload("summaryText", "利用者の要約", "quotes", List.of()))),
        false);

    assertEquals(
        "今日は頂点の求め方を教わりました。",
        ai.summarize(context(OTHER, AiCallKind.SUMMARY), summaryInput()).summaryText());
    assertEquals(
        "利用者の要約", ai.summarize(context(USER, AiCallKind.SUMMARY), summaryInput()).summaryText());
    assertEquals(1, ai.callCounts(OTHER).get(AiCallKind.SUMMARY));
    assertEquals(1, ai.callCounts(USER).get(AiCallKind.SUMMARY));
  }

  @Test
  void 呼び出しの数えは_種類ごとに数え_呼ばれていない種類は_0_で返す() {
    ai.transcribe(context(USER, AiCallKind.STT), audio());
    ai.transcribe(context(USER, AiCallKind.STT), audio());

    Map<AiCallKind, Integer> counts = ai.callCounts(USER);

    assertEquals(AiCallKind.values().length, counts.size());
    assertEquals(2, counts.get(AiCallKind.STT));
    assertEquals(0, counts.get(AiCallKind.TTS));
  }

  @Test
  void 認識結果は_空の文字列も返せる() {
    ai.script(USER, AiCallKind.STT, List.of(success(payload("text", ""))), false);

    assertEquals("", ai.transcribe(context(USER, AiCallKind.STT), audio()));
  }

  @Test
  void 音声合成は_中身を持たないため_既定の無音を返す() {
    ai.script(USER, AiCallKind.TTS, List.of(success(null)), false);

    Media speech =
        ai.synthesize(context(USER, AiCallKind.TTS), new Voice.Student(StudentType.LOGICAL), "はい");

    assertEquals("audio/mpeg", speech.mimeType());
    assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xFB}, Arrays.copyOf(speech.bytes(), 2));
  }

  @Test
  void reset_は_待ち列と数えを消す() {
    ai.script(USER, AiCallKind.STT, List.of(failure()), false);
    assertThrows(
        AiFailedException.class, () -> ai.transcribe(context(USER, AiCallKind.STT), audio()));

    ai.reset(USER);

    assertEquals(0, ai.callCounts(USER).get(AiCallKind.STT));
    assertEquals("ここの式変形が分かりません", ai.transcribe(context(USER, AiCallKind.STT), audio()));
  }

  @Test
  void 形の合わない_payload_は_待ち列を変えずに_IllegalArgumentException() {
    ai.script(USER, AiCallKind.STT, List.of(success(payload("text", "一つ目"))), false);

    assertThrows(
        IllegalArgumentException.class,
        () -> ai.script(USER, AiCallKind.STT, List.of(success(payload("text", 1))), false));

    assertEquals("一つ目", ai.transcribe(context(USER, AiCallKind.STT), audio()));
  }

  @Test
  void 形の合わない_payload_を_種類ごとに拒む() {
    assertRejected(AiCallKind.EVALUATION, payload("degrees", List.of(1, 1)));
    assertRejected(AiCallKind.EVALUATION, payload("degrees", List.of(1, 1, 3)));
    assertRejected(AiCallKind.STUDENT_REPLY, payload("text", "x", "expression", "angry"));
    assertRejected(AiCallKind.MOCK_EXAM, payload("problemText", "x", "answerSheet", "y"));
    assertRejected(AiCallKind.HOMEWORK, payload("verdict", "maybe", "comment", "c"));
    assertRejected(AiCallKind.ORGANIZE, payload("problemText", "x", "modelAnswer", "y"));
  }

  @Test
  void 応答が空の指定は_IllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class, () -> ai.script(USER, AiCallKind.STT, List.of(), false));
  }

  private void assertRejected(AiCallKind kind, Map<String, Object> payload) {
    assertThrows(
        IllegalArgumentException.class,
        () -> ai.script(USER, kind, List.of(success(payload)), false),
        kind.wireName());
  }
}
