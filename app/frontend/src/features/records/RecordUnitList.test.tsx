import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { describe, expect, test } from "vitest";
import { RecordUnitList } from "./RecordUnitList";
import { UNIT_ID } from "./fixtures";
import { recordUnitsQuery, type RecordUnitsResponse } from "./use-records";

// 一覧の応答は、テストが先に置く。staleTime を無限にして、画面が取り直しの通信をしないようにする
function renderList(units: RecordUnitsResponse["units"]) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  client.setQueryData(recordUnitsQuery.queryKey, { units });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <RecordUnitList />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("RecordUnitList (学習記録の一覧)", () => {
  test("記録の無い単元は、学習記録がありませんを出す", () => {
    renderList([]);

    expect(screen.getByText("学習記録がありません")).toBeTruthy();
  });

  test("記録のある単元を、教科と単元名で並べ、単元の記録へのリンクにする", () => {
    renderList([
      { unitId: UNIT_ID, subject: "math", unitName: "二次関数", lessonCount: 3, mockExamCount: 1 },
    ]);

    const link = screen.getByRole("link", { name: "数学・二次関数" });
    expect(link.getAttribute("href")).toBe(`/records/units/${UNIT_ID}`);
    expect(screen.queryByText("学習記録がありません")).toBeNull();
  });
});
