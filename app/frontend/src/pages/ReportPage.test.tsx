import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { describe, expect, test } from "vitest";
import { RECORD_UNIT, UNIT_ID } from "../features/records/fixtures";
import { recordUnitQuery, type RecordUnit } from "../features/records/use-records";
import { ReportPage } from "./ReportPage";

// 単元の記録は、テストが先に置く。staleTime を無限にして、画面が取り直しの通信をしないようにする
function renderReport(unit: RecordUnit) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  client.setQueryData(recordUnitQuery(UNIT_ID).queryKey, unit);
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[`/records/units/${UNIT_ID}/report`]}>
        <Routes>
          <Route path="/records/units/:unitId/report" element={<ReportPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("ReportPage (学習レポート)", () => {
  test("授業が 1 回以上あるときは、画像を保存を出す", () => {
    renderReport(RECORD_UNIT);

    expect(screen.getByRole("button", { name: "画像を保存" })).toBeTruthy();
  });

  test("授業が 1 回も無いときは、まだ授業がありませんを出し、画像を保存は出さない", () => {
    renderReport({
      ...RECORD_UNIT,
      report: { unitName: "二次関数", lessonCount: 0, taughtProblemCount: 0, latestMockExam: null },
    });

    expect(screen.getByText("まだ授業がありません", { exact: true })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "画像を保存" })).toBeNull();
  });
});
