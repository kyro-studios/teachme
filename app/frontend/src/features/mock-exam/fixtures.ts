import type { MockExamResult } from "../../api/queries";

/** 模試の結果の見本 (得点 90、偏差値 69.0、偏差値の推移は 1 件) */
export const MOCK_EXAM_RESULT: MockExamResult = {
  id: "9b1d7c2e-3f4a-4b5c-8d6e-7f8a9b0c1d2e",
  takenAt: "2026-10-21T16:00:00Z",
  unitName: "二次関数",
  score: 90,
  deviation: 69,
  problemText: "y = x^2 - 4x + 3 の最小値を求めなさい",
  answerSheet: "x = 2 で最小値 -1",
  studentComment: "先生の説明で頂点の求め方は分かりました",
  history: [{ takenAt: "2026-10-21T16:00:00Z", deviation: 69 }],
};
