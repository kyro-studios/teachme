import { expect, test } from "vitest";
import {
  clamp01,
  expandRegion,
  fitLongSide,
  frameToRegion,
  regionToFrame,
  unionRegions,
} from "./geometry";

test("0 から 1 の範囲に収める", () => {
  expect(clamp01(-0.2)).toBe(0);
  expect(clamp01(0.4)).toBe(0.4);
  expect(clamp01(1.3)).toBe(1);
});

test("割合の矩形を、枠 (ページの単位) の中の矩形にする", () => {
  const frame = { x: 1000, y: 200, width: 1600, height: 900 };

  expect(regionToFrame({ x: 0.5, y: 0.25, width: 0.25, height: 0.5 }, frame)).toEqual({
    x: 1800,
    y: 425,
    width: 400,
    height: 450,
  });
});

test("長辺が上限以下なら縮小せず、拡大もしない", () => {
  expect(fitLongSide(1024, 768, 1280)).toBe(1);
  expect(fitLongSide(1280, 800, 1280)).toBe(1);
});

test("長辺が上限を超えるときは、長辺が上限に収まる比率で縮小する", () => {
  const scale = fitLongSide(2560, 1440, 1280);

  expect(scale).toBe(0.5);
  expect(2560 * scale).toBeLessThanOrEqual(1280);
  expect(1440 * scale).toBe(720);
});

test("縦長の画像も、長辺で縮小する", () => {
  const scale = fitLongSide(900, 2400, 1280);

  expect(2400 * scale).toBe(1280);
  expect(900 * scale).toBeCloseTo(480);
});

test("ページの矩形を、枠に対する割合にする。枠の外へ出る分は端に収める", () => {
  const frame = { x: 1000, y: 200, width: 1600, height: 900 };

  expect(frameToRegion({ x: 1800, y: 425, width: 400, height: 450 }, frame)).toEqual({
    x: 0.5,
    y: 0.25,
    width: 0.25,
    height: 0.5,
  });
  expect(frameToRegion({ x: 0, y: 0, width: 5000, height: 5000 }, frame)).toEqual({
    x: 0,
    y: 0,
    width: 1,
    height: 1,
  });
});

test("割合の矩形は、同じ枠の中でページの矩形に戻せる", () => {
  // 送った画像の枠を変えなければ、描いた場所と AI の書き込みの場所は一致する
  const sentFrame = { x: -300, y: 40, width: 2000, height: 1200 };
  const page = { x: 120, y: 900, width: 260, height: 80 };

  const fraction = frameToRegion(page, sentFrame);

  const back = regionToFrame(fraction, sentFrame);
  expect(back.x).toBeCloseTo(page.x);
  expect(back.y).toBeCloseTo(page.y);
  expect(back.width).toBeCloseTo(page.width);
  expect(back.height).toBeCloseTo(page.height);
});

test("矩形の集まりを囲む矩形を返す。画面の外の矩形も含める", () => {
  const union = unionRegions([
    { x: 0, y: 0, width: 480, height: 300 },
    { x: 0, y: 340, width: 480, height: 240 },
    { x: 2400, y: 1200, width: 300, height: 200 },
  ]);

  expect(union).toEqual({ x: 0, y: 0, width: 2700, height: 1400 });
});

test("矩形が無いときは、囲む矩形は無い", () => {
  expect(unionRegions([])).toBeNull();
});

test("矩形の四方を、同じ幅だけ広げる", () => {
  expect(expandRegion({ x: 10, y: 20, width: 100, height: 50 }, 16)).toEqual({
    x: -6,
    y: 4,
    width: 132,
    height: 82,
  });
});
