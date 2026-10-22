package com.teachme.domain;

import java.util.List;
import java.util.Optional;

/**
 * 教材の整理の結果 (仕様書 5.3、6 節。アーキテクチャ設計 4.2)。
 *
 * <p>形を外れる結果は、{@link #parse} で無効とする。解説は 1 件以上、指導のポイントは 3 件、図表は 5
 * 件以下で、図表の画像の番号は教材の画像の枚数の範囲、矩形は画像の中に収まること。 文字列の項目は、null を許さない (保存先の列が null を受け付けないため)。
 */
public final class OrganizedProblem {

  /** 指導のポイントの件数 (仕様書 9 節)。 */
  public static final int TEACHING_POINT_COUNT = 3;

  /** 図表の件数の上限 (openapi.yaml の `Problem.figures`)。 */
  public static final int MAX_FIGURES = 5;

  private final String problemText;
  private final List<Figure> figures;
  private final String modelAnswer;
  private final List<String> explanationSteps;
  private final String studentWork;
  private final List<String> teachingPoints;

  private OrganizedProblem(
      String problemText,
      List<Figure> figures,
      String modelAnswer,
      List<String> explanationSteps,
      String studentWork,
      List<String> teachingPoints) {
    this.problemText = problemText;
    this.figures = figures;
    this.modelAnswer = modelAnswer;
    this.explanationSteps = explanationSteps;
    this.studentWork = studentWork;
    this.teachingPoints = teachingPoints;
  }

  /**
   * 整理の結果を検査して作る。形を外れれば空を返す。
   *
   * @param imageCount 教材の画像の枚数 (1 から 3)。図表の画像の番号は、この範囲に入る
   * @return 形が正しければ整理の結果、外れていれば空
   */
  public static Optional<OrganizedProblem> parse(
      String problemText,
      List<Figure> figures,
      String modelAnswer,
      List<String> explanationSteps,
      String studentWork,
      List<String> teachingPoints,
      int imageCount) {
    if (problemText == null
        || modelAnswer == null
        || studentWork == null
        || figures == null
        || explanationSteps == null
        || explanationSteps.isEmpty()
        || teachingPoints == null
        || teachingPoints.size() != TEACHING_POINT_COUNT
        || containsNull(teachingPoints)
        || containsNull(explanationSteps)
        || figures.size() > MAX_FIGURES
        || !figures.stream().allMatch(figure -> isValidFigure(figure, imageCount))) {
      return Optional.empty();
    }
    return Optional.of(
        new OrganizedProblem(
            problemText,
            List.copyOf(figures),
            modelAnswer,
            List.copyOf(explanationSteps),
            studentWork,
            List.copyOf(teachingPoints)));
  }

  public String problemText() {
    return problemText;
  }

  /** 問題の図表。無ければ空。 */
  public List<Figure> figures() {
    return figures;
  }

  public String modelAnswer() {
    return modelAnswer;
  }

  public List<String> explanationSteps() {
    return explanationSteps;
  }

  public String studentWork() {
    return studentWork;
  }

  /** 指導のポイント (3 件)。 */
  public List<String> teachingPoints() {
    return teachingPoints;
  }

  /**
   * 問題の図表。教材の画像の 1 枚の中の、矩形の部分。
   *
   * @param imagePosition 教材の画像の番号 (1 から)
   * @param region 画像の中の矩形
   */
  public record Figure(int imagePosition, Region region) {}

  /** 対象の画像全体に対する、0 以上 1 以下の割合で表した矩形。左上が原点。 */
  public record Region(double x, double y, double width, double height) {

    /** 0 以上 1 以下の範囲に収まり、幅と高さは 0 より大きい。NaN と無限大は外れとする。 */
    public boolean fitsInImage() {
      return Double.isFinite(x)
          && Double.isFinite(y)
          && Double.isFinite(width)
          && Double.isFinite(height)
          && x >= 0
          && y >= 0
          && width > 0
          && height > 0
          && x + width <= 1
          && y + height <= 1;
    }
  }

  private static boolean containsNull(List<String> items) {
    return items.stream().anyMatch(item -> item == null);
  }

  private static boolean isValidFigure(Figure figure, int imageCount) {
    return figure != null
        && figure.region() != null
        && figure.imagePosition() >= 1
        && figure.imagePosition() <= imageCount
        && figure.region().fitsInImage();
  }
}
