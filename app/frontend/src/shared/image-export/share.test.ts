import { toBlob } from "html-to-image";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { canShareImage, shareElementAsPng } from "./share";

vi.mock("html-to-image", () => ({ toBlob: vi.fn() }));

const PNG_BLOB = new Blob(["png"], { type: "image/png" });

function stubNavigator(share?: () => Promise<void>, canShare?: () => boolean) {
  vi.stubGlobal("navigator", { share, canShare });
}

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

test("share と canShare があり、PNG を受け付けるときは、共有できると判定する", () => {
  const canShare = vi.fn(() => true);
  stubNavigator(vi.fn(), canShare);

  expect(canShareImage()).toBe(true);
  const [data] = canShare.mock.calls[0] as unknown as [{ files: File[] }];
  expect(data.files[0].type).toBe("image/png");
});

test("share が無い端末では、共有できないと判定する", () => {
  stubNavigator(undefined, () => true);

  expect(canShareImage()).toBe(false);
});

test("canShare が無い端末では、共有できないと判定する", () => {
  stubNavigator(vi.fn(), undefined);

  expect(canShareImage()).toBe(false);
});

test("canShare が画像を受け付けないときは、共有できないと判定する", () => {
  stubNavigator(vi.fn(), () => false);

  expect(canShareImage()).toBe(false);
});

test("canShare が例外を投げたときは、共有できないと判定する", () => {
  stubNavigator(vi.fn(), () => {
    throw new TypeError("files を扱えません");
  });

  expect(canShareImage()).toBe(false);
});

test("PNG にして、ファイル名とタイトルを付けて Web Share API へ渡す", async () => {
  const share = vi.fn(async () => {});
  stubNavigator(share, () => true);
  vi.mocked(toBlob).mockResolvedValue(PNG_BLOB);

  await shareElementAsPng(document.createElement("div"), "teachme-report.png", "学習レポート");

  expect(share).toHaveBeenCalledTimes(1);
  const [data] = share.mock.calls[0] as unknown as [{ files: File[]; title: string }];
  expect(data.title).toBe("学習レポート");
  expect(data.files[0].name).toBe("teachme-report.png");
  expect(data.files[0].type).toBe("image/png");
});

test("利用者が共有の画面を閉じたときは、失敗としない", async () => {
  stubNavigator(
    vi.fn(async () => {
      throw new DOMException("キャンセルされました", "AbortError");
    }),
    () => true,
  );
  vi.mocked(toBlob).mockResolvedValue(PNG_BLOB);

  await expect(
    shareElementAsPng(document.createElement("div"), "teachme-report.png", "学習レポート"),
  ).resolves.toBeUndefined();
});

test("共有に失敗したら、例外を投げる", async () => {
  stubNavigator(
    vi.fn(async () => {
      throw new DOMException("共有できませんでした", "NotAllowedError");
    }),
    () => true,
  );
  vi.mocked(toBlob).mockResolvedValue(PNG_BLOB);

  await expect(
    shareElementAsPng(document.createElement("div"), "teachme-report.png", "学習レポート"),
  ).rejects.toThrow();
});

test("共有に対応していない端末では、PNG を作らず例外を投げる", async () => {
  stubNavigator(undefined, undefined);

  await expect(
    shareElementAsPng(document.createElement("div"), "teachme-report.png", "学習レポート"),
  ).rejects.toThrow();

  expect(toBlob).not.toHaveBeenCalled();
});
