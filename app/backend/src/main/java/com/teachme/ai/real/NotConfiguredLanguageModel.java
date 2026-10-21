package com.teachme.ai.real;

import com.teachme.ai.AiCallContext;
import com.teachme.ai.AiCallKind;
import com.teachme.ai.AiFailedException;
import com.teachme.ai.LanguageModel;
import com.teachme.ai.Media;
import java.util.List;

/**
 * 実際の LLM の実装が入るまでの代わり (`AI_MODE=real` のとき)。どの呼び出しもネットワークへ出ず、失敗として返す。
 *
 * <p>失敗は画面の失敗の表示になり、進行は止まらない (仕様書 8 節)。実装が入ったら、置き換える。
 */
public final class NotConfiguredLanguageModel implements LanguageModel {

  @Override
  public OrganizeResult organize(AiCallContext context, List<Media> images) {
    throw new AiFailedException(AiCallKind.ORGANIZE);
  }

  @Override
  public StudentReply studentReply(AiCallContext context, StudentReplyInput input) {
    throw new AiFailedException(AiCallKind.STUDENT_REPLY);
  }

  @Override
  public Evaluation evaluate(AiCallContext context, EvaluationInput input) {
    throw new AiFailedException(AiCallKind.EVALUATION);
  }

  @Override
  public MentorReply mentorReply(AiCallContext context, MentorReplyInput input) {
    throw new AiFailedException(AiCallKind.MENTOR_REPLY);
  }

  @Override
  public SummaryResult summarize(AiCallContext context, SummaryInput input) {
    throw new AiFailedException(AiCallKind.SUMMARY);
  }

  @Override
  public MockExamResult mockExam(AiCallContext context, MockExamInput input) {
    throw new AiFailedException(AiCallKind.MOCK_EXAM);
  }

  @Override
  public HomeworkJudgement gradeHomework(AiCallContext context, Media photo) {
    throw new AiFailedException(AiCallKind.HOMEWORK);
  }
}
