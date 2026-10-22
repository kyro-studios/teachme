import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, test, vi } from "vitest";
import { RecordUnitDetail } from "./RecordUnitDetail";
import { RECORD_UNIT, UNIT_ID } from "./fixtures";

const DELETE_URL = `/api/records/units/${UNIT_ID}`;

function renderDetail() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[`/records/units/${UNIT_ID}`]}>
        <Routes>
          <Route path="/records/units/:unitId" element={<RecordUnitDetail unit={RECORD_UNIT} />} />
          <Route path="/records" element={<p>学習記録の一覧</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("RecordUnitDetail (単元の記録)", () => {
  test("まとめノート、模試の結果、学習レポートの見出しを出す", () => {
    renderDetail();

    expect(screen.getByRole("region", { name: "まとめノート" })).toBeTruthy();
    expect(screen.getByRole("region", { name: "模試の結果" })).toBeTruthy();
    expect(screen.getByRole("region", { name: "学習レポート" })).toBeTruthy();
  });

  test("学習レポートの見出しは、学習レポートの画面へのリンクになる", () => {
    renderDetail();

    const link = screen.getByRole("link", { name: "学習レポート" });
    expect(link.getAttribute("href")).toBe(`/records/units/${UNIT_ID}/report`);
  });

  test("削除は確認を 1 つだけ出し、やめるでは削除しない", () => {
    const fetchMock = vi.fn(async () => new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    renderDetail();

    fireEvent.click(screen.getByRole("button", { name: "この単元の学習記録を削除" }));
    expect(
      screen.getByRole("alertdialog", { name: "この単元の学習記録を削除しますか？" }),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "やめる" }));

    expect(fetchMock).not.toHaveBeenCalled();
  });

  test("削除するを押すと、削除の API を 1 回呼び、学習記録の一覧へ戻る", async () => {
    const fetchMock = vi.fn(async () => new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    renderDetail();

    fireEvent.click(screen.getByRole("button", { name: "この単元の学習記録を削除" }));
    fireEvent.click(screen.getByRole("button", { name: "削除する" }));

    expect(await screen.findByText("学習記録の一覧")).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith(
      DELETE_URL,
      expect.objectContaining({ method: "DELETE" }),
    );
  });

  test("削除の実行中は、削除のボタンを無効にする", async () => {
    // 応答を返さないままにして、実行中の状態を保つ
    vi.stubGlobal(
      "fetch",
      vi.fn(() => new Promise<Response>(() => {})),
    );
    renderDetail();

    fireEvent.click(screen.getByRole("button", { name: "この単元の学習記録を削除" }));
    fireEvent.click(screen.getByRole("button", { name: "削除する" }));

    await waitFor(() =>
      expect(
        (screen.getByRole("button", { name: "この単元の学習記録を削除" }) as HTMLButtonElement)
          .disabled,
      ).toBe(true),
    );
  });
});
