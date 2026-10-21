import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { toBlob } from "html-to-image";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { ImageExportButtons } from "./ImageExportButtons";

vi.mock("html-to-image", () => ({ toBlob: vi.fn() }));

const PNG_BLOB = new Blob(["png"], { type: "image/png" });
const ERROR = "画像を保存できませんでした";

function renderButtons() {
  const target = { current: document.createElement("div") };
  render(<ImageExportButtons target={target} fileName="teachme-report.png" title="学習レポート" />);
  return target;
}

function stubNavigator(share?: () => Promise<void>, canShare?: () => boolean) {
  vi.stubGlobal("navigator", { share, canShare });
}

let clickedDownloads: string[] = [];

beforeEach(() => {
  vi.clearAllMocks();
  clickedDownloads = [];
  URL.createObjectURL = vi.fn(() => "blob:teachme-test");
  URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (
    this: HTMLAnchorElement,
  ) {
    clickedDownloads.push(this.download);
  });
  stubNavigator(undefined, undefined);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

test("画像を保存を押すと、PNG をダウンロードし、エラーを出さない", async () => {
  vi.mocked(toBlob).mockResolvedValue(PNG_BLOB);
  renderButtons();

  fireEvent.click(screen.getByRole("button", { name: "画像を保存" }));

  await waitFor(() => expect(clickedDownloads).toEqual(["teachme-report.png"]));
  expect(screen.queryByRole("alert")).toBeNull();
});

test("PNG にできないときは、「画像を保存できませんでした」を出す", async () => {
  vi.mocked(toBlob).mockRejectedValue(new Error("描画に失敗しました"));
  renderButtons();

  fireEvent.click(screen.getByRole("button", { name: "画像を保存" }));

  expect((await screen.findByRole("alert")).textContent).toBe(ERROR);
  expect(clickedDownloads).toEqual([]);
});

test("共有に対応していない端末では、「共有する」を出さない", () => {
  renderButtons();

  expect(screen.queryByRole("button", { name: "共有する" })).toBeNull();
});

test("共有に対応した端末では、「共有する」で Web Share API に画像を渡す", async () => {
  const share = vi.fn(async () => {});
  stubNavigator(share, () => true);
  vi.mocked(toBlob).mockResolvedValue(PNG_BLOB);
  renderButtons();

  fireEvent.click(screen.getByRole("button", { name: "共有する" }));

  await waitFor(() => expect(share).toHaveBeenCalledTimes(1));
  const [data] = share.mock.calls[0] as unknown as [{ files: File[]; title: string }];
  expect(data.title).toBe("学習レポート");
  expect(data.files[0].name).toBe("teachme-report.png");
});

test("共有に失敗したら、同じ「画像を保存できませんでした」を出す", async () => {
  stubNavigator(
    vi.fn(async () => {
      throw new DOMException("共有できませんでした", "NotAllowedError");
    }),
    () => true,
  );
  vi.mocked(toBlob).mockResolvedValue(PNG_BLOB);
  renderButtons();

  fireEvent.click(screen.getByRole("button", { name: "共有する" }));

  expect((await screen.findByRole("alert")).textContent).toBe(ERROR);
});
