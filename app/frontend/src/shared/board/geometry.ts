// ボードの座標の計算。tldraw や DOM に依存しない純粋な関数だけを置く

/**
 * 対象の画像全体 (ボードの画像、または教材の画像) に対する、0 から 1 の割合で表した矩形。左上が原点。
 * openapi.yaml の `Region` と同じ形
 */
export type Region = { x: number; y: number; width: number; height: number };

/** 対象の画像全体に対する、0 から 1 の割合で表した点 */
export type Point = { x: number; y: number };

/** 画面上の枠 (ボードの要素の矩形)。`DOMRect` の形の一部 */
export type ScreenFrame = { left: number; top: number; width: number; height: number };

/** 0 から 1 の範囲に収める */
export function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value));
}

/** 画面上の点を、枠に対する割合の点にする。枠の外は 0 か 1 に収める */
export function toFraction(clientX: number, clientY: number, frame: ScreenFrame): Point {
  if (frame.width <= 0 || frame.height <= 0) {
    return { x: 0, y: 0 };
  }
  return {
    x: clamp01((clientX - frame.left) / frame.width),
    y: clamp01((clientY - frame.top) / frame.height),
  };
}

/** 割合の矩形を、ボードの枠 (ページの単位) の中の矩形にする */
export function regionToFrame(region: Region, frame: Region): Region {
  return {
    x: frame.x + region.x * frame.width,
    y: frame.y + region.y * frame.height,
    width: region.width * frame.width,
    height: region.height * frame.height,
  };
}

/**
 * 長辺が `maxLongSide` を超えるときの縮小率を返す。超えないときは 1 (拡大はしない)。
 * 縮小した後の長辺は、`maxLongSide` 以下になる
 */
export function fitLongSide(width: number, height: number, maxLongSide: number): number {
  const longSide = Math.max(width, height);
  return longSide > maxLongSide ? maxLongSide / longSide : 1;
}
