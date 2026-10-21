import { expect, test } from "vitest";
import { clamp01, fitLongSide, regionToFrame, toFraction } from "./geometry";

test("画面上の点は、枠に対する割合にし、枠の外は 0 か 1 に収める", () => {
  const frame = { left: 100, top: 50, width: 800, height: 600 };

  expect(toFraction(500, 350, frame)).toEqual({ x: 0.5, y: 0.5 });
  expect(toFraction(0, 0, frame)).toEqual({ x: 0, y: 0 });
  expect(toFraction(2000, 2000, frame)).toEqual({ x: 1, y: 1 });
});

test("枠の大きさが 0 のときは、原点として扱う", () => {
  expect(toFraction(10, 10, { left: 0, top: 0, width: 0, height: 0 })).toEqual({ x: 0, y: 0 });
});

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
