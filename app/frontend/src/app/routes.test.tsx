import { render, screen, waitFor } from "@testing-library/react";
import { RouterProvider } from "react-router/dom";
import { createMemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { routes } from "./routes";
import { queryClient } from "./query-client";

/** GET /config と GET /settings に答える。completed で利用者の設定の状態を決める */
function stubApi(completed: boolean) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    const body = url.endsWith("/config") ? { aiMode: "fake" } : { completed, settings: null };
    return new Response(JSON.stringify(body), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  });
  vi.stubGlobal("fetch", fetchMock);
}

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
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
