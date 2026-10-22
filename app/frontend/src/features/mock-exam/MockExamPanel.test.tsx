import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, test, vi } from "vitest";
import { MockExamPanel } from "./MockExamPanel";
import { MOCK_EXAM_RESULT } from "./fixtures";

const HOME = {
  student: {
    id: "logical",
    displayName: "論理・ツッコミ派",
    initialDeviation: 60,
    targetDeviation: 65,
  },
  subject: "math",
  unitId: "6f1c2a8e-7b1d-4d0e-9c2a-3f5e8b7a1d20",
  unitName: "二次関数",
  lessonsThisWeek: 0,
  lessonsPerWeek: 3,
  mockExamAvailable: true,
  todayMode: "normal",
  hasOrganizedMaterial: true,
};

/**
 * 画面が読む API だけを決めた応答で返す。
 * GET /api/home は機会の有無を返し、POST /api/mock-exams は `respond` の応答 (呼び出しの回数つき) を返す
 */
function stubApi(respond: (call: number) => Promise<Response> | Response) {
  let mockExamCalls = 0;
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/home") {
      return Response.json(HOME);
    }
    if (url === "/api/mock-exams" && init?.method === "POST") {
      mockExamCalls += 1;
      return respond(mockExamCalls);
    }
    throw new Error(`想定していない呼び出し: ${init?.method ?? "GET"} ${url}`);
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/home"]}>
        <Routes>
          <Route path="/home" element={<MockExamPanel />} />
          <Route path="/mock-exams/:mockExamId" element={<p>模試の結果の画面</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("MockExamPanel (ホームの「模試に挑戦」)", () => {
  test("押すと模試を作り、結果の画面へ進む", async () => {
    const fetchMock = stubApi(() => Response.json(MOCK_EXAM_RESULT, { status: 201 }));
    renderPanel();

    fireEvent.click(await screen.findByRole("button", { name: "模試に挑戦" }));

    expect(await screen.findByText("模試の結果の画面")).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/mock-exams",
      expect.objectContaining({ method: "POST" }),
    );
  });

  test("作成が失敗したら「模試を作れませんでした」と「やり直す」を出し、やり直すと作り直す", async () => {
    const fetchMock = stubApi((call) =>
      call === 1
        ? Response.json(
            { error: { code: "mock_exam_failed", message: "模試を作れませんでした" } },
            { status: 502 },
          )
        : Response.json(MOCK_EXAM_RESULT, { status: 201 }),
    );
    renderPanel();

    fireEvent.click(await screen.findByRole("button", { name: "模試に挑戦" }));
    expect(await screen.findByText("模試を作れませんでした")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "模試に挑戦" })).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "やり直す" }));
    expect(await screen.findByText("模試の結果の画面")).toBeTruthy();
    expect(fetchMock.mock.calls.filter(([url]) => url === "/api/mock-exams")).toHaveLength(2);
  });

  test("作成の途中は押せないようにし、二重に呼ばない", async () => {
    let finish: (response: Response) => void = () => {};
    const fetchMock = stubApi(
      () =>
        new Promise<Response>((resolve) => {
          finish = resolve;
        }),
    );
    renderPanel();

    const button = await screen.findByRole("button", { name: "模試に挑戦" });
    fireEvent.click(button);
    fireEvent.click(button);

    await waitFor(() =>
      expect(
        (screen.getByRole("button", { name: "模試に挑戦" }) as HTMLButtonElement).disabled,
      ).toBe(true),
    );
    expect(fetchMock.mock.calls.filter(([url]) => url === "/api/mock-exams")).toHaveLength(1);

    finish(Response.json(MOCK_EXAM_RESULT, { status: 201 }));
    expect(await screen.findByText("模試の結果の画面")).toBeTruthy();
  });
});
