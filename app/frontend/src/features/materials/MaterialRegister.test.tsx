import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, test, vi } from "vitest";
import { MaterialRegister } from "./MaterialRegister";

const CONFIRM = "写真に氏名、学校名、連絡先が写っていないことを確認しました";

function renderRegister() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MaterialRegister />
    </QueryClientProvider>,
  );
}

function jpeg(size = 1024): File {
  const file = new File(["jpeg"], "material.jpg", { type: "image/jpeg" });
  Object.defineProperty(file, "size", { value: size });
  return file;
}

function selectImages(files: File[]) {
  const input = screen.getByLabelText("教材を登録") as HTMLInputElement;
  fireEvent.change(input, { target: { files } });
}

function registerButton() {
  return screen.getByRole("button", { name: "登録する" });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("MaterialRegister (教材の登録)", () => {
  test("画像を選ぶまで、確認のチェックは出さず、登録するも押せない", () => {
    renderRegister();

    expect(screen.queryByRole("checkbox", { name: CONFIRM })).toBeNull();
    expect(registerButton().hasAttribute("disabled")).toBe(true);
  });

  test("画像を選んでも、確認のチェックが無ければ登録するを押せない", () => {
    renderRegister();

    selectImages([jpeg()]);

    expect(screen.getByRole("checkbox", { name: CONFIRM })).toBeTruthy();
    expect(registerButton().hasAttribute("disabled")).toBe(true);
  });

  test("画像を選び、確認のチェックを入れると、登録するを押せる", () => {
    renderRegister();

    selectImages([jpeg()]);
    fireEvent.click(screen.getByRole("checkbox", { name: CONFIRM }));

    expect(registerButton().hasAttribute("disabled")).toBe(false);
  });

  test("4 枚以上を選ぶと、枚数の文言を出し、確認のチェックを出さない", () => {
    renderRegister();

    selectImages([jpeg(), jpeg(), jpeg(), jpeg()]);

    expect(screen.getByRole("alert").textContent).toBe("画像は 1 枚から 3 枚まで選んでください");
    expect(screen.queryByRole("checkbox", { name: CONFIRM })).toBeNull();
  });

  test("登録すると、画像と確認のチェックを送り、選んだ画像を外す", async () => {
    const fetchMock = vi.fn(async () =>
      Response.json({ id: "m1", number: 1, status: "organizing" }, { status: 202 }),
    );
    vi.stubGlobal("fetch", fetchMock);
    renderRegister();
    selectImages([jpeg()]);
    fireEvent.click(screen.getByRole("checkbox", { name: CONFIRM }));

    fireEvent.click(registerButton());

    await waitFor(() => expect(registerButton().hasAttribute("disabled")).toBe(true));
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/materials");
    expect(init.method).toBe("POST");
    const body = init.body as FormData;
    expect(body.getAll("images")).toHaveLength(1);
    expect(body.get("photoConfirmed")).toBe("true");
  });

  test("登録が失敗したら、API の文言を出す", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json(
          { error: { code: "photo_not_confirmed", message: "写真の確認をしてください" } },
          { status: 400 },
        ),
      ),
    );
    renderRegister();
    selectImages([jpeg()]);
    fireEvent.click(screen.getByRole("checkbox", { name: CONFIRM }));

    fireEvent.click(registerButton());

    expect(await screen.findByText("写真の確認をしてください")).toBeTruthy();
  });
});
