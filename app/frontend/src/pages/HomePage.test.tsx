import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, test, vi } from "vitest";
import { HomePage } from "./HomePage";

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
  mockExamAvailable: false,
  todayMode: "normal",
  hasOrganizedMaterial: true,
};

// 画面が読む API (GET /api/home、PUT /api/today-mode) だけを、決めた応答で返す
function stubApi(initial: typeof HOME) {
  let current = initial;
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/today-mode" && init?.method === "PUT") {
      const body = JSON.parse(String(init.body)) as { mode: string };
      current = { ...current, todayMode: body.mode };
    }
    return Response.json(current);
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function renderHome() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/home"]}>
        <HomePage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("HomePage (ホーム)", () => {
  test("通常のとき、授業を始めると、2 つの切り替えのボタンを出す", async () => {
    stubApi(HOME);
    renderHome();

    expect(await screen.findByRole("button", { name: "授業を始める" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "自習指示に切り替える" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "宿題の丸つけに切り替える" })).toBeTruthy();
    expect(screen.getByText("今週の授業 0 / 3 回")).toBeTruthy();
  });

  test("自習指示に切り替えると、指示の文を出し、授業を始めるを出さない", async () => {
    const fetchMock = stubApi(HOME);
    renderHome();
    fireEvent.click(await screen.findByRole("button", { name: "自習指示に切り替える" }));

    expect(await screen.findByText("今日は自習指示の日です")).toBeTruthy();
    expect(
      screen.getByText("今日は「二次関数」の教材から、問題を 1 つ解いてみてください。"),
    ).toBeTruthy();
    expect(screen.queryByRole("button", { name: "授業を始める" })).toBeNull();
    expect(screen.getByRole("button", { name: "通常に戻す" })).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/today-mode",
      expect.objectContaining({ method: "PUT", body: JSON.stringify({ mode: "self_study" }) }),
    );
  });

  test("通常に戻すと、授業を始めるを出し、指示の文を出さない", async () => {
    stubApi({ ...HOME, todayMode: "self_study" });
    renderHome();
    fireEvent.click(await screen.findByRole("button", { name: "通常に戻す" }));

    await waitFor(() => expect(screen.getByRole("button", { name: "授業を始める" })).toBeTruthy());
    expect(screen.queryByText("今日は自習指示の日です")).toBeNull();
  });

  test("宿題の丸つけのとき、写真の登録と判定を出し、授業を始めるを出さない", async () => {
    stubApi({ ...HOME, todayMode: "homework_check" });
    renderHome();

    expect(await screen.findByText("今日は宿題の丸つけの日です")).toBeTruthy();
    expect(screen.getByLabelText("宿題の写真を登録")).toBeTruthy();
    expect(screen.getByRole("button", { name: "判定する" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "授業を始める" })).toBeNull();
  });

  test("模試の機会があるときだけ、模試に挑戦を出す", async () => {
    stubApi({ ...HOME, mockExamAvailable: true });
    renderHome();

    expect(await screen.findByRole("button", { name: "模試に挑戦" })).toBeTruthy();
  });
});
