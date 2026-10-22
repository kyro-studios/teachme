// 学習記録の画面の単体テストで使う、API の応答の例 (docs/api/openapi.yaml の RecordUnit、LearningReport)
import type { LearningReport, RecordUnit } from "./use-records";

export const UNIT_ID = "6f1c2a8e-7b1d-4d0e-9c2a-3f5e8b7a1d20";

export const RECORD_UNIT: RecordUnit = {
  unitId: UNIT_ID,
  subject: "math",
  unitName: "二次関数",
  notes: [
    { recordId: "11111111-1111-4111-8111-111111111111", completedAt: "2026-10-21T03:00:00Z" },
  ],
  mockExams: [
    {
      id: "22222222-2222-4222-8222-222222222222",
      takenAt: "2026-10-21T04:00:00Z",
      score: 90,
      deviation: 69,
    },
  ],
  report: {
    unitName: "二次関数",
    lessonCount: 3,
    taughtProblemCount: 1,
    latestMockExam: { score: 90, deviation: 69 },
  },
};

export const REPORT_WITHOUT_MOCK_EXAM: LearningReport = {
  unitName: "二次関数",
  lessonCount: 1,
  taughtProblemCount: 1,
  latestMockExam: null,
};
