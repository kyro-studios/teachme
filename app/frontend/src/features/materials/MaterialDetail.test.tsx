import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, test, vi } from "vitest";
import { cropFigure } from "../../shared/board/figure";
import { MaterialDetail } from "./MaterialDetail";
import type { MaterialDetail as MaterialDetailData } from "./use-materials";

// 切り出しは canvas を使うため、テストでは決まった結果を返す
vi.mock("../../shared/board/figure", () => ({
  cropFigure: vi.fn(async () => ({
    blob: new Blob(["png"], { type: "image/png" }),
    width: 10,
    height: 10,
  })),
}));

const ORGANIZED: MaterialDetailData = {
  id: "m1",
  number: 1,
  status: "organized",
  problem: {
    problemText: "y = x^2 - 4x + 3 の頂点を求めなさい",
    figures: [],
    modelAnswer: "頂点は (2, -1)",
    explanationSteps: ["平方完成して頂点を読む"],
    studentWork: "y = (x - 2)^2 と書いて手が止まっている",
    teachingPoints: ["平方完成の形を作る", "括弧の中の数を読む", "頂点の y 座標を求める"],
  },
};

function renderDetail(material: MaterialDetailData) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/materials/m1"]}>
        <Routes>
          <Route path="/materials/:materialId" element={<MaterialDetail material={material} />} />
          <Route path="/materials" element={<p>先生準備室の一覧</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe("MaterialDetail (教材の詳細)", () => {
  test("整理済みの教材は、5 つの見出しの下に結果を出し、指導のポイントは 3 件にする", () => {
    renderDetail(ORGANIZED);

    for (const heading of ["問題", "模範解答", "解説", "生徒の途中の解答", "指導のポイント"]) {
      expect(screen.getByRole("heading", { name: heading })).toBeTruthy();
    }
    expect(
      within(screen.getByRole("region", { name: "問題" })).getByText(
        "y = x^2 - 4x + 3 の頂点を求めなさい",
      ),
    ).toBeTruthy();
    expect(
      within(screen.getByRole("region", { name: "模範解答" })).getByText("頂点は (2, -1)"),
    ).toBeTruthy();
    expect(
      within(screen.getByRole("region", { name: "解説" })).getByText("平方完成して頂点を読む"),
    ).toBeTruthy();
    expect(
      within(screen.getByRole("region", { name: "指導のポイント" })).getAllByRole("listitem"),
    ).toHaveLength(3);
  });

  test("整理中は、整理していますを出し、教材を削除は出さない", () => {
    renderDetail({ id: "m1", number: 1, status: "organizing", problem: null });

    expect(screen.getByText("整理しています")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "教材を削除" })).toBeNull();
    expect(screen.queryByRole("heading", { name: "問題" })).toBeNull();
  });

  test("整理できなかった教材は、整理できませんでしたと、やり直すを出す", () => {
    renderDetail({ id: "m1", number: 1, status: "failed", problem: null });

    expect(screen.getByText("整理できませんでした")).toBeTruthy();
    expect(screen.getByRole("button", { name: "やり直す" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "教材を削除" })).toBeTruthy();
  });

  test("図表は、教材の画像を取り、切り出した画像を問題の中に表示する", async () => {
    vi.stubGlobal(
      "URL",
      Object.assign(URL, { createObjectURL: vi.fn(() => "blob:figure"), revokeObjectURL: vi.fn() }),
    );
    const fetchMock = vi.fn(
      async () => new Response("jpeg", { headers: { "Content-Type": "image/jpeg" } }),
    );
    vi.stubGlobal("fetch", fetchMock);
    const region = { x: 0.1, y: 0.2, width: 0.5, height: 0.4 };

    renderDetail({
      ...ORGANIZED,
      problem: { ...ORGANIZED.problem!, figures: [{ imagePosition: 2, region }] },
    });

    expect(await screen.findByRole("img", { name: "問題の図表 1" })).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledWith("/api/materials/m1/images/2");
    expect(vi.mocked(cropFigure)).toHaveBeenCalledWith(expect.anything(), region);
  });

  test("教材を削除を確認して削除すると、教材を消して一覧へ戻る", async () => {
    const fetchMock = vi.fn(async () => new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    renderDetail(ORGANIZED);

    fireEvent.click(screen.getByRole("button", { name: "教材を削除" }));
    fireEvent.click(screen.getByRole("button", { name: "削除する" }));

    expect(await screen.findByText("先生準備室の一覧")).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/materials/m1",
      expect.objectContaining({ method: "DELETE" }),
    );
  });

  test("やめるを押すと、削除の API を呼ばない", () => {
    const fetchMock = vi.fn(async () => new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    renderDetail(ORGANIZED);

    fireEvent.click(screen.getByRole("button", { name: "教材を削除" }));
    fireEvent.click(screen.getByRole("button", { name: "やめる" }));

    expect(fetchMock).not.toHaveBeenCalled();
    expect(screen.getByRole("heading", { name: "問題" })).toBeTruthy();
  });
});
