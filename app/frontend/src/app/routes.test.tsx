import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { RouterProvider } from "react-router/dom";
import { createMemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { routes } from "./routes";
import { queryClient } from "./query-client";

const STUDENT_TYPES = [
  { id: "intuitive", displayName: "直感・感覚派", initialDeviation: 45, targetDeviation: 50 },
  { id: "diligent", displayName: "真面目な暗記派", initialDeviation: 52, targetDeviation: 58 },
  { id: "logical", displayName: "論理・ツッコミ派", initialDeviation: 60, targetDeviation: 65 },
];

const HOME = {
  student: STUDENT_TYPES[2],
  subject: "math",
  unitId: "00000000-0000-4000-8000-000000000001",
  unitName: "二次関数",
  lessonsThisWeek: 0,
  lessonsPerWeek: 3,
  mockExamAvailable: false,
  todayMode: "normal",
  hasOrganizedMaterial: false,
};

/** GET /config、GET /settings、GET /student-types、GET /home に答える。completed で利用者の設定の状態を決める */
function stubApi(completed: boolean) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    let body: unknown;
    if (url.endsWith("/config")) {
      body = { aiMode: "fake" };
    } else if (url.endsWith("/student-types")) {
      body = { studentTypes: STUDENT_TYPES };
    } else if (url.endsWith("/home")) {
      body = HOME;
    } else {
      body = { completed, settings: null };
    }
    return new Response(JSON.stringify(body), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  });
  vi.stubGlobal("fetch", fetchMock);
}

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
}

beforeEach(() => {
  queryClient.clear();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("入口の判定 (routes)", () => {
  test("設定が完了していれば、別の画面から「/」へ遷移するとホームを表示する", async () => {
    stubApi(true);
    const router = renderAt("/materials");
    expect(await screen.findByRole("heading", { name: "先生準備室" })).toBeTruthy();

    await router.navigate("/");

    expect(await screen.findByRole("heading", { name: "ホーム" })).toBeTruthy();
    expect(router.state.location.pathname).toBe("/home");
  });

  test("設定が未完了なら、別の画面から遷移しても初期セットアップへ寄せる", async () => {
    stubApi(false);
    const router = renderAt("/setup");
    expect(await screen.findByRole("heading", { name: "初期セットアップ" })).toBeTruthy();

    await router.navigate("/materials");

    await waitFor(() => expect(router.state.location.pathname).toBe("/setup"));
    expect(screen.getByRole("heading", { name: "初期セットアップ" })).toBeTruthy();
  });
});
