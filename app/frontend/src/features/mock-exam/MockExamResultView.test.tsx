import { render, screen, within } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { MockExamResultView } from "./MockExamResultView";
import { MOCK_EXAM_RESULT } from "./fixtures";

describe("MockExamResultView (模試の結果の表示)", () => {
  test("見出しと、得点と偏差値の項目を出す", () => {
    render(<MockExamResultView result={MOCK_EXAM_RESULT} />);

    expect(screen.getByRole("heading", { name: "解答用紙" })).toBeTruthy();
    expect(screen.getByRole("heading", { name: "生徒のコメント" })).toBeTruthy();
    expect(screen.getByRole("heading", { name: "偏差値の推移" })).toBeTruthy();
    expect(within(screen.getByRole("group", { name: "得点" })).getByText("90")).toBeTruthy();
    expect(within(screen.getByRole("group", { name: "偏差値" })).getByText("69.0")).toBeTruthy();
  });

  test("解答用紙と生徒のコメントを、本文として出す", () => {
    render(<MockExamResultView result={MOCK_EXAM_RESULT} />);

    expect(screen.getByText("x = 2 で最小値 -1")).toBeTruthy();
    expect(screen.getByText("先生の説明で頂点の求め方は分かりました")).toBeTruthy();
  });

  test("偏差値の推移を、日付と偏差値の一覧で古い順に出す", () => {
    render(
      <MockExamResultView
        result={{
          ...MOCK_EXAM_RESULT,
          history: [
            { takenAt: "2026-10-01T03:00:00Z", deviation: 60 },
            { takenAt: "2026-10-21T16:00:00Z", deviation: 69 },
          ],
        }}
      />,
    );

    const items = screen.getByRole("list", { name: "偏差値の推移" }).querySelectorAll("li");
    expect(items).toHaveLength(2);
    expect(items[0].textContent).toBe("2026/10/0160.0");
    expect(items[1].textContent).toBe("2026/10/2269.0");
  });
});
