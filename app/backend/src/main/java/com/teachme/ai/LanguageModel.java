package com.teachme.ai;

import com.teachme.domain.StudentType;
import com.teachme.domain.Subject;
import java.util.List;

/**
 * LLM の呼び出し (アーキテクチャ設計 4.3)。教材の整理、生徒の応答、学習評価、先輩先生の応答、まとめ、模試、宿題の丸つけ。
 *
 * <p>どのメソッドも、最初の引数に {@link AiCallContext} を受け取る。失敗は {@link AiFailedException} で表す。
 *
 * <p>入力と出力の型は、外部 AI に渡してよい内容だけを持つ。`studentReply` の入力は、模範解答、解説、指導のポイント、学習評価を持たない (仕様書 5.5)。
 */
public interface LanguageModel {

  /**
   * @param images 教材の画像 (1 枚から 3 枚)
   */
  OrganizeResult organize(AiCallContext context, List<Media> images);

  StudentReply studentReply(AiCallContext context, StudentReplyInput input);

  Evaluation evaluate(AiCallContext context, EvaluationInput input);

  MentorReply mentorReply(AiCallContext context, MentorReplyInput input);

  SummaryResult summarize(AiCallContext context, SummaryInput input);

  MockExamResult mockExam(AiCallContext context, MockExamInput input);

  /**
   * @param photo 宿題の写真
   */
  HomeworkJudgement gradeHomework(AiCallContext context, Media photo);

  /**
   * 教材の整理の結果。`OrganizedProblem` の元で、形の検査は domain が行う。
   *
   * @param figures 問題の図表。無ければ空
   */
  record OrganizeResult(
      String problemText,
      List<Figure> figures,
      String modelAnswer,
      List<String> explanationSteps,
      String studentWork,
      List<String> teachingPoints) {}

  /**
   * 問題の図表。教材の画像の 1 枚の中の、矩形の部分。
   *
   * @param imagePosition 教材の画像の番号 (1 から)
   * @param region 画像の中の矩形
   */
  record Figure(int imagePosition, Region region) {}

  /** 対象の画像全体に対する、0 以上 1 以下の割合で表した矩形。左上が原点。 */
  record Region(double x, double y, double width, double height) {}

  /** 字幕の話し手。 */
  enum Speaker {
    USER,
    STUDENT,
    MENTOR
  }

  /** 字幕の 1 行。 */
  record Subtitle(Speaker speaker, String text) {}

  /**
   * 生徒の応答の入力。模範解答、解説、指導のポイント、学習評価は持たない (仕様書 5.5)。
   *
   * @param studentWork 生徒の途中の解答
   * @param boardImage ボードの画像
   * @param pointedRegions 指している範囲。無ければ空
   */
  record StudentReplyInput(
      StudentType studentType,
      String problemText,
      String studentWork,
      Media boardImage,
      String utterance,
      List<Subtitle> subtitles,
      List<Region> pointedRegions) {}

  /** 生徒の表情。`考え中` は、クライアントだけが使うため含めない。 */
  enum StudentExpression {
    NORMAL,
    TROUBLED,
    CONVINCED
  }

  /** ボードに示す印の種類。 */
  enum AnnotationKind {
    POINT,
    QUESTION_MARK
  }

  /** ボードに示す箇所か、「？」の印。 */
  record StudentAnnotation(AnnotationKind kind, Region region) {}

  /**
   * 生徒の応答。
   *
   * @param annotation 無ければ null
   */
  record StudentReply(String text, StudentExpression expression, StudentAnnotation annotation) {}

  /**
   * 学習評価の入力。
   *
   * @param teachingPoints 指導のポイント (3 件)
   */
  record EvaluationInput(
      List<String> teachingPoints,
      String modelAnswer,
      List<String> explanationSteps,
      String problemText,
      Media boardImage,
      String utterance,
      List<Subtitle> subtitles,
      List<Region> pointedRegions) {}

  /**
   * 学習評価の結果。指導のポイントごとの度合い (0 から 2)。
   *
   * @param degrees 指導のポイントの順に並ぶ度合い
   */
  record Evaluation(List<Integer> degrees) {}

  /** 先輩先生の応答の入力。 */
  record MentorReplyInput(
      String problemText,
      String modelAnswer,
      List<String> explanationSteps,
      List<String> teachingPoints,
      Media boardImage,
      String utterance,
      List<Subtitle> subtitles,
      List<Region> pointedRegions) {}

  /** ボードへの書き込みの種類。 */
  enum BoardWriteKind {
    TEXT,
    HIGHLIGHT
  }

  /**
   * ボードへの書き込み。
   *
   * @param text `TEXT` のときの内容
   * @param region 範囲。無ければ null
   */
  record BoardWrite(BoardWriteKind kind, String text, Region region) {}

  /** 先輩先生の応答。 */
  record MentorReply(String text, List<BoardWrite> boardWrites) {}

  /** まとめの入力。 */
  record SummaryInput(StudentType studentType, List<Subtitle> subtitles, Media boardImage) {}

  /**
   * まとめの結果。
   *
   * @param quotes 引用する板書の文章。無ければ空
   */
  record SummaryResult(String summaryText, List<String> quotes) {}

  /**
   * 模試の入力。
   *
   * @param lastLesson 直近の授業の問題とポイント。無ければ null
   */
  record MockExamInput(
      StudentType studentType,
      Subject subject,
      String unitName,
      int score,
      LastLesson lastLesson) {}

  /** 直近の授業の問題とポイント。 */
  record LastLesson(String problemText, List<String> teachingPoints) {}

  /** 模試の結果 (類題、解答用紙、生徒のコメント)。 */
  record MockExamResult(String problemText, String answerSheet, String studentComment) {}

  /** 宿題の判定。 */
  enum HomeworkVerdict {
    CORRECT,
    INCORRECT,
    UNDETERMINED
  }

  /** 宿題の丸つけの結果。 */
  record HomeworkJudgement(HomeworkVerdict verdict, String comment) {}
}
