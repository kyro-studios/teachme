// ボードの座標の計算。tldraw や DOM に依存しない純粋な関数だけを置く

/**
 * 対象の画像全体 (ボードの画像、または教材の画像) に対する、0 から 1 の割合で表した矩形。左上が原点。
 * openapi.yaml の `Region` と同じ形
 */
export type Region = { x: number; y: number; width: number; height: number };

/** 対象の画像全体に対する、0 から 1 の割合で表した点 */
export type Point = { x: number; y: number };

/** 0 から 1 の範囲に収める */
export function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value));
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
 * ページの単位の矩形を、枠に対する割合の矩形にする (`regionToFrame` の逆)。
 * 枠の外へ出る分は、枠の端に収める。枠の幅と高さは 0 より大きいこと
 */
export function frameToRegion(rect: Region, frame: Region): Region {
  const left = clamp01((rect.x - frame.x) / frame.width);
  const top = clamp01((rect.y - frame.y) / frame.height);
  const right = clamp01((rect.x + rect.width - frame.x) / frame.width);
  const bottom = clamp01((rect.y + rect.height - frame.y) / frame.height);
  return { x: left, y: top, width: right - left, height: bottom - top };
}

/** 矩形の集まりを囲む矩形。集まりが空のときは null */
export function unionRegions(regions: readonly Region[]): Region | null {
  if (regions.length === 0) {
    return null;
  }
  const left = Math.min(...regions.map((region) => region.x));
  const top = Math.min(...regions.map((region) => region.y));
  const right = Math.max(...regions.map((region) => region.x + region.width));
  const bottom = Math.max(...regions.map((region) => region.y + region.height));
  return { x: left, y: top, width: right - left, height: bottom - top };
}

/** 矩形の四方を、同じ幅だけ広げる */
export function expandRegion(region: Region, margin: number): Region {
  return {
    x: region.x - margin,
    y: region.y - margin,
    width: region.width + margin * 2,
    height: region.height + margin * 2,
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
