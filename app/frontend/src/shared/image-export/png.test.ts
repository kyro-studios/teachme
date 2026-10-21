import { toBlob } from "html-to-image";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { downloadPng, elementToPng } from "./png";

vi.mock("html-to-image", () => ({ toBlob: vi.fn() }));

const PNG_BLOB = new Blob(["png"], { type: "image/png" });

// 押されたリンクの download と href を、後で確かめるために記録する
let clicked: { download: string; href: string }[] = [];

beforeEach(() => {
  vi.clearAllMocks();
  clicked = [];
  URL.createObjectURL = vi.fn(() => "blob:teachme-test");
  URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (
    this: HTMLAnchorElement,
  ) {
    clicked.push({ download: this.download, href: this.href });
  });
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

test("要素を PNG の Blob にする", async () => {
  vi.mocked(toBlob).mockResolvedValue(PNG_BLOB);
  const element = document.createElement("div");

  const blob = await elementToPng(element);

  expect(toBlob).toHaveBeenCalledWith(element);
  expect(blob).toBe(PNG_BLOB);
});

test("PNG にできなかったら、例外を投げる", async () => {
  vi.mocked(toBlob).mockResolvedValue(null);

  await expect(elementToPng(document.createElement("div"))).rejects.toThrow();
});

test("PNG をファイル名を付けてダウンロードさせ、あとで URL を失効させる", async () => {
  vi.useFakeTimers();
  vi.mocked(toBlob).mockResolvedValue(PNG_BLOB);

  await downloadPng(document.createElement("div"), "teachme-report.png");

  expect(clicked).toEqual([{ download: "teachme-report.png", href: "blob:teachme-test" }]);
  expect(URL.revokeObjectURL).not.toHaveBeenCalled();
  vi.runAllTimers();
  expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:teachme-test");
});

test("PNG の作成に失敗したら、ダウンロードせず例外を投げる", async () => {
  vi.mocked(toBlob).mockRejectedValue(new Error("描画に失敗しました"));

  await expect(downloadPng(document.createElement("div"), "teachme-report.png")).rejects.toThrow(
    "描画に失敗しました",
  );

  expect(clicked).toEqual([]);
});
