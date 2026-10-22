import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, test, vi } from "vitest";
import { MOCK_EXAM_RESULT } from "../features/mock-exam/fixtures";
import { MockExamPage } from "./MockExamPage";

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/mock-exams/:mockExamId" element={<MockExamPage />} />
          <Route path="/home" element={<p>ホームの画面</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("MockExamPage (模試の結果)", () => {
  test("模試の結果を取り、見出しと「画像を保存」を出す", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json(MOCK_EXAM_RESULT)),
    );
    renderAt(`/mock-exams/${MOCK_EXAM_RESULT.id}`);

    expect(await screen.findByRole("heading", { level: 1, name: "模試の結果" })).toBeTruthy();
    expect(screen.getByText("x = 2 で最小値 -1")).toBeTruthy();
    expect(screen.getByRole("button", { name: "画像を保存" })).toBeTruthy();
  });

  test("模試が無いときは、ホームへ戻す", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({ error: { code: "not_found", message: "見つかりません" } }, { status: 404 }),
      ),
    );
    renderAt(`/mock-exams/${MOCK_EXAM_RESULT.id}`);

    expect(await screen.findByText("ホームの画面")).toBeTruthy();
  });
});
