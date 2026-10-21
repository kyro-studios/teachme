import { toBlob } from "html-to-image";

// 対象の要素を PNG の Blob にする。サーバーには送らない。失敗したら例外を投げる
export async function elementToPng(element: HTMLElement): Promise<Blob> {
  const blob = await toBlob(element);
  if (!blob) {
    throw new Error("PNG の作成に失敗しました");
  }
  return blob;
}

// 対象の要素を PNG にして、ファイルとしてダウンロードさせる
export async function downloadPng(element: HTMLElement, fileName: string): Promise<void> {
  const blob = await elementToPng(element);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.append(link);
  link.click();
  link.remove();
  // 直後に失効させると、一部のブラウザでダウンロードが始まらないため、少し遅らせる
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}
