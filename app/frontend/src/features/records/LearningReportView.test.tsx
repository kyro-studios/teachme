import { render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { LearningReportView } from "./LearningReportView";
import { RECORD_UNIT, REPORT_WITHOUT_MOCK_EXAM } from "./fixtures";

describe("LearningReportView (学習レポートの内容)", () => {
  test("単元名、授業の回数、教えた問題の数、最新の模試を並べる", () => {
    render(<LearningReportView report={RECORD_UNIT.report} />);

    expect(screen.getByRole("heading", { name: "学習レポート" })).toBeTruthy();
    expect(screen.getByText("単元名: 二次関数", { exact: true })).toBeTruthy();
    expect(screen.getByText("授業の回数: 3 回", { exact: true })).toBeTruthy();
    expect(screen.getByText("教えた問題の数: 1 問", { exact: true })).toBeTruthy();
    expect(screen.getByText("最新の模試: 得点 90、偏差値 69.0", { exact: true })).toBeTruthy();
  });

  test("模試が無いときは、まだ模試がありませんを出す", () => {
    render(<LearningReportView report={REPORT_WITHOUT_MOCK_EXAM} />);

    expect(screen.getByText("まだ模試がありません", { exact: true })).toBeTruthy();
    expect(screen.queryByText(/最新の模試/)).toBeNull();
  });

  test("授業が 1 回も無いときは、まだ授業がありませんだけを出し、件数を出さない", () => {
    render(
      <LearningReportView
        report={{
          unitName: "二次関数",
          lessonCount: 0,
          taughtProblemCount: 0,
          latestMockExam: null,
        }}
      />,
    );

    expect(screen.getByText("まだ授業がありません", { exact: true })).toBeTruthy();
    expect(screen.queryByText(/授業の回数/)).toBeNull();
    expect(screen.queryByText(/まだ模試がありません/)).toBeNull();
  });
});
