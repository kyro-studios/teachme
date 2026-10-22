import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, test, vi } from "vitest";
import { MaterialList } from "./MaterialList";

function renderList(materials: { id: string; number: number; status: string }[]) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => Response.json({ materials })),
  );
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <MaterialList />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("MaterialList (先生準備室の教材の一覧)", () => {
  test("教材が無いと、教材はまだありませんを表示する", async () => {
    renderList([]);

    expect(await screen.findByText("教材はまだありません")).toBeTruthy();
  });

  test("教材を番号と状態の表示名で並べ、詳細へのリンクを付ける", async () => {
    renderList([
      { id: "m1", number: 1, status: "organized" },
      { id: "m2", number: 2, status: "organizing" },
    ]);

    expect(await screen.findByRole("link", { name: "教材 1" })).toBeTruthy();
    expect(screen.getByRole("link", { name: "教材 1" }).getAttribute("href")).toBe("/materials/m1");
    expect(screen.getByRole("link", { name: "教材 2" }).getAttribute("href")).toBe("/materials/m2");
    expect(screen.getByText("整理済み")).toBeTruthy();
    expect(screen.getByText("整理中")).toBeTruthy();
  });

  test("整理に失敗した教材だけに、整理できませんでしたとやり直すを出す", async () => {
    renderList([
      { id: "m1", number: 1, status: "failed" },
      { id: "m2", number: 2, status: "organized" },
    ]);

    expect(await screen.findByText("整理できませんでした")).toBeTruthy();
    expect(screen.getAllByRole("button", { name: "やり直す" })).toHaveLength(1);
  });
});
