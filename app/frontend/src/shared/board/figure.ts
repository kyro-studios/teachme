// 図表の切り出し。教材の画像と割合の矩形から、PNG を作る。
// tldraw に依存しないため、教材の詳細からも使える
import type { Region } from "./geometry";

/** 画像の中の、ピクセルで表した矩形。整数で、画像の外に出ない */
export type PixelRect = { x: number; y: number; width: number; height: number };

/** 切り出した図表の PNG と、その大きさ */
export type CroppedFigure = { blob: Blob; width: number; height: number };

/**
 * 割合の矩形を、画像の大きさに合わせてピクセルの矩形にする。
 * 境界は四捨五入し、画像の外へ出る分は切り詰める。幅と高さは、1 px 以上にする。
 * 画像の大きさは 1 px 以上であること
 */
export function cropRect(size: { width: number; height: number }, region: Region): PixelRect {
  const x = toPixelSpan(region.x, region.width, size.width);
  const y = toPixelSpan(region.y, region.height, size.height);
  return { x: x.from, y: y.from, width: x.length, height: y.length };
}

function toPixelSpan(
  start: number,
  length: number,
  size: number,
): { from: number; length: number } {
  const from = Math.min(Math.max(Math.round(start * size), 0), size - 1);
  const to = Math.min(Math.max(Math.round((start + length) * size), from + 1), size);
  return { from, length: to - from };
}

/**
 * 教材の画像から `region` の部分を切り出し、PNG にする。
 * 画像は、整理の結果の `GET /materials/{id}/images/{position}` で取得した Blob を渡す
 */
export async function cropFigure(image: Blob, region: Region): Promise<CroppedFigure> {
  const bitmap = await createImageBitmap(image);
  try {
    const rect = cropRect({ width: bitmap.width, height: bitmap.height }, region);
    const canvas = document.createElement("canvas");
    canvas.width = rect.width;
    canvas.height = rect.height;
    const context = canvas.getContext("2d");
    if (!context) {
      throw new Error("図表を描くための canvas を用意できませんでした");
    }
    context.drawImage(
      bitmap,
      rect.x,
      rect.y,
      rect.width,
      rect.height,
      0,
      0,
      rect.width,
      rect.height,
    );
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/png"));
    if (!blob) {
      throw new Error("図表を PNG にできませんでした");
    }
    return { blob, width: rect.width, height: rect.height };
  } finally {
    bitmap.close();
  }
}

/** Blob を、画像の図形の素材に使える data URL にする */
export function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}
