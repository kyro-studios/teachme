import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, test, vi } from "vitest";
import { HomeworkCheck } from "./HomeworkCheck";

const CONFIRM = "写真に氏名、学校名、連絡先が写っていないことを確認しました";

function renderCheck() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <HomeworkCheck />
    </QueryClientProvider>,
  );
}

function selectPhoto(file: File) {
  const input = screen.getByLabelText("宿題の写真を登録") as HTMLInputElement;
  fireEvent.change(input, { target: { files: [file] } });
}

function jpeg(size = 1024): File {
  const file = new File(["jpeg"], "homework.jpg", { type: "image/jpeg" });
  Object.defineProperty(file, "size", { value: size });
  return file;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("HomeworkCheck (宿題の丸つけ)", () => {
  test("写真を選ぶまで、確認のチェックは出さず、判定するも押せない", () => {
    renderCheck();

    expect(screen.queryByRole("checkbox", { name: CONFIRM })).toBeNull();
    expect(screen.getByRole("button", { name: "判定する" }).hasAttribute("disabled")).toBe(true);
  });

  test("写真を選んでも、確認のチェックが無ければ判定するを押せない", () => {
    renderCheck();

    selectPhoto(jpeg());

    expect(screen.getByRole("checkbox", { name: CONFIRM })).toBeTruthy();
    expect(screen.getByRole("button", { name: "判定する" }).hasAttribute("disabled")).toBe(true);
  });

  test("写真を選び、確認のチェックを入れると、判定するを押せる", () => {
    renderCheck();

    selectPhoto(jpeg());
    fireEvent.click(screen.getByRole("checkbox", { name: CONFIRM }));

    expect(screen.getByRole("button", { name: "判定する" }).hasAttribute("disabled")).toBe(false);
  });

  test("JPEG か PNG でない写真は、エラーを出し、確認のチェックを出さない", () => {
    renderCheck();

    selectPhoto(new File(["gif"], "homework.gif", { type: "image/gif" }));

    expect(screen.getByRole("alert").textContent).toBe(
      "画像は JPEG か PNG で、1 枚 10 MB 以下にしてください",
    );
    expect(screen.queryByRole("checkbox", { name: CONFIRM })).toBeNull();
  });

  test("判定の結果を、判定の欄に文言とコメントで表示する", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ verdict: "incorrect", comment: "符号を見直しましょう" })),
    );
    renderCheck();
    selectPhoto(jpeg());
    fireEvent.click(screen.getByRole("checkbox", { name: CONFIRM }));

    fireEvent.click(screen.getByRole("button", { name: "判定する" }));

    expect(await screen.findByRole("heading", { name: "判定" })).toBeTruthy();
    expect(screen.getByText("不正解")).toBeTruthy();
    expect(screen.getByText("符号を見直しましょう")).toBeTruthy();
  });

  test("判定できなかったときは、撮り直す文言を出す", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ verdict: "undetermined", comment: "" })),
    );
    renderCheck();
    selectPhoto(jpeg());
    fireEvent.click(screen.getByRole("checkbox", { name: CONFIRM }));

    fireEvent.click(screen.getByRole("button", { name: "判定する" }));

    await waitFor(() =>
      expect(screen.getByText("判定できませんでした。撮り直してください")).toBeTruthy(),
    );
  });
});
