package com.teachme.ai.fake;

import com.teachme.ai.LanguageModel.Evaluation;
import com.teachme.ai.LanguageModel.HomeworkJudgement;
import com.teachme.ai.LanguageModel.HomeworkVerdict;
import com.teachme.ai.LanguageModel.MentorReply;
import com.teachme.ai.LanguageModel.MockExamResult;
import com.teachme.ai.LanguageModel.OrganizeResult;
import com.teachme.ai.LanguageModel.StudentExpression;
import com.teachme.ai.LanguageModel.StudentReply;
import com.teachme.ai.LanguageModel.SummaryResult;
import com.teachme.ai.Media;
import java.util.List;

/** 既定の成功の応答 (openapi.yaml の `AiScript` の x-defaults)。何も決めていないときに返す。 */
final class FakeDefaults {

  private static final int SILENT_FRAME_COUNT = 10;

  /** MPEG-1 Layer III、128 kbps、44.1 kHz の 1 フレーム (417 バイト) の先頭。残りは 0 で、無音になる。 */
  private static final int MP3_FRAME_LENGTH = 417;

  private FakeDefaults() {}

  static OrganizeResult organize() {
    return new OrganizeResult(
        "y = x^2 - 4x + 3 の頂点を求めなさい",
        List.of(),
        "頂点は (2, -1)",
        List.of("平方完成して頂点を読む"),
        "y = (x - 2)^2 と書いて手が止まっている",
        List.of("平方完成の形を作る", "括弧の中の数を読む", "頂点の y 座標を求める"));
  }

  static StudentReply studentReply() {
    return new StudentReply("なぜそうなるのですか？", StudentExpression.TROUBLED, null);
  }

  static Evaluation evaluation() {
    return new Evaluation(List.of(1, 1, 1));
  }

  static MentorReply mentorReply() {
    return new MentorReply("ここは平方完成を使うと考えやすいですよ。", List.of());
  }

  static SummaryResult summary() {
    return new SummaryResult("今日は頂点の求め方を教わりました。", List.of());
  }

  static MockExamResult mockExam() {
    return new MockExamResult("y = x^2 - 6x + 5 の頂点を求めなさい", "平方完成して頂点を求めた", "先生の説明で解けました");
  }

  static HomeworkJudgement homework() {
    return new HomeworkJudgement(HomeworkVerdict.CORRECT, "よくできました");
  }

  static String transcript() {
    return "ここの式変形が分かりません";
  }

  /** 短い無音の mp3。呼び出しごとに新しい配列を返す。 */
  static Media silentSpeech() {
    byte[] bytes = new byte[MP3_FRAME_LENGTH * SILENT_FRAME_COUNT];
    for (int frame = 0; frame < SILENT_FRAME_COUNT; frame++) {
      int offset = frame * MP3_FRAME_LENGTH;
      bytes[offset] = (byte) 0xFF;
      bytes[offset + 1] = (byte) 0xFB;
      bytes[offset + 2] = (byte) 0x90;
      bytes[offset + 3] = 0x00;
    }
    return new Media("audio/mpeg", bytes);
  }
}
