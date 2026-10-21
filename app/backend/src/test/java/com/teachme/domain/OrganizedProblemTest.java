package com.teachme.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.teachme.domain.OrganizedProblem.Figure;
import com.teachme.domain.OrganizedProblem.Region;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrganizedProblemTest {
  private static final List<String> POINTS = List.of("平方完成をする", "頂点を読む", "符号に注意する");
  private static final List<String> STEPS = List.of("平方完成の形を作る");

  private static Figure figure(int imagePosition, double x, double y, double width, double height) {
    return new Figure(imagePosition, new Region(x, y, width, height));
  }

  /** 画像の枚数 1 の教材で、図表が無い整理の結果を作る。項目を 1 つずつ変えて使う。 */
  private static Optional<OrganizedProblem> parse(
      List<String> points, List<Figure> figures, int imageCount) {
    return OrganizedProblem.parse(
        "y = x^2 - 4x + 3 の頂点を求めなさい",
        figures,
        "頂点は (2, -1)",
        STEPS,
        "y = (x - 2)^2 と書いて手が止まっている",
        points,
        imageCount);
  }

  @Test
  void 指導のポイントが_3_件で_図表が無ければ_整理の結果になる() {
    OrganizedProblem problem = parse(POINTS, List.of(), 1).orElseThrow();

    assertEquals("y = x^2 - 4x + 3 の頂点を求めなさい", problem.problemText());
    assertEquals(POINTS, problem.teachingPoints());
    assertEquals(STEPS, problem.explanationSteps());
    assertTrue(problem.figures().isEmpty());
  }

  @Test
  void 指導のポイントが_2_件や_4_件のときは無効() {
    assertTrue(parse(List.of("a", "b"), List.of(), 1).isEmpty());
    assertTrue(parse(List.of("a", "b", "c", "d"), List.of(), 1).isEmpty());
    assertTrue(parse(List.of(), List.of(), 1).isEmpty());
  }

  @Test
  void 指導のポイントに_null_が入るときは無効() {
    List<String> withNull = Arrays.asList("a", null, "c");

    assertTrue(parse(withNull, List.of(), 1).isEmpty());
  }

  @Test
  void 図表は_5_件まで有効で_6_件は無効() {
    List<Figure> five =
        List.of(
            figure(1, 0, 0, 0.1, 0.1),
            figure(1, 0.1, 0, 0.1, 0.1),
            figure(1, 0.2, 0, 0.1, 0.1),
            figure(1, 0.3, 0, 0.1, 0.1),
            figure(1, 0.4, 0, 0.1, 0.1));
    List<Figure> six = new ArrayList<>(five);
    six.add(figure(1, 0.5, 0, 0.1, 0.1));

    assertEquals(5, parse(POINTS, five, 1).orElseThrow().figures().size());
    assertTrue(parse(POINTS, six, 1).isEmpty());
  }

  @Test
  void 図表の画像の番号は_1_から_教材の画像の枚数まで() {
    assertTrue(parse(POINTS, List.of(figure(0, 0, 0, 0.5, 0.5)), 3).isEmpty());
    assertTrue(parse(POINTS, List.of(figure(4, 0, 0, 0.5, 0.5)), 3).isEmpty());
    assertTrue(parse(POINTS, List.of(figure(3, 0, 0, 0.5, 0.5)), 3).isPresent());
    assertTrue(parse(POINTS, List.of(figure(2, 0, 0, 0.5, 0.5)), 1).isEmpty());
  }

  @Test
  void 矩形は_0_以上_1_以下の範囲に収まる() {
    assertTrue(parse(POINTS, List.of(figure(1, -0.1, 0, 0.5, 0.5)), 1).isEmpty());
    assertTrue(parse(POINTS, List.of(figure(1, 0, -0.1, 0.5, 0.5)), 1).isEmpty());
    // 右端と下端が、ちょうど 1 の場合は収まっている
    assertTrue(parse(POINTS, List.of(figure(1, 0.5, 0.5, 0.5, 0.5)), 1).isPresent());
    assertTrue(parse(POINTS, List.of(figure(1, 0.6, 0, 0.5, 0.5)), 1).isEmpty());
    assertTrue(parse(POINTS, List.of(figure(1, 0, 0.6, 0.5, 0.5)), 1).isEmpty());
  }

  @Test
  void 矩形の幅と高さは_0_より大きい() {
    assertTrue(parse(POINTS, List.of(figure(1, 0, 0, 0, 0.5)), 1).isEmpty());
    assertTrue(parse(POINTS, List.of(figure(1, 0, 0, 0.5, -0.5)), 1).isEmpty());
  }

  @Test
  void 矩形に_NaN_や無限大が入るときは無効() {
    assertTrue(parse(POINTS, List.of(figure(1, Double.NaN, 0, 0.5, 0.5)), 1).isEmpty());
    assertTrue(parse(POINTS, List.of(figure(1, 0, 0, Double.POSITIVE_INFINITY, 0.5)), 1).isEmpty());
  }

  @Test
  void 文字列の項目に_null_があるときは無効() {
    assertTrue(OrganizedProblem.parse(null, List.of(), "答え", STEPS, "途中", POINTS, 1).isEmpty());
    assertTrue(OrganizedProblem.parse("問題", List.of(), "答え", STEPS, null, POINTS, 1).isEmpty());
  }
}
