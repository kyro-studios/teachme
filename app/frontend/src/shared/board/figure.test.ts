import { expect, test } from "vitest";
import { cropRect } from "./figure";

test("割合の矩形を、画像のピクセルの矩形にする", () => {
  const rect = cropRect({ width: 1000, height: 800 }, { x: 0.1, y: 0.25, width: 0.5, height: 0.5 });

  expect(rect).toEqual({ x: 100, y: 200, width: 500, height: 400 });
});

test("境界は四捨五入する", () => {
  // 始点の 0.4 px は 0 に、終点の 2.4 px は 2 に丸める
  const rect = cropRect({ width: 10, height: 10 }, { x: 0.04, y: 0.04, width: 0.2, height: 0.2 });

  expect(rect).toEqual({ x: 0, y: 0, width: 2, height: 2 });
});

test("画像の外へ出る分は切り詰める", () => {
  const rect = cropRect({ width: 1000, height: 500 }, { x: 0.9, y: 0.8, width: 0.5, height: 0.5 });

  expect(rect).toEqual({ x: 900, y: 400, width: 100, height: 100 });
  expect(rect.x + rect.width).toBeLessThanOrEqual(1000);
  expect(rect.y + rect.height).toBeLessThanOrEqual(500);
});

test("幅と高さが 0 の範囲でも、1 ピクセル以上の矩形にする", () => {
  const rect = cropRect({ width: 1000, height: 1000 }, { x: 0.5, y: 0.5, width: 0, height: 0 });

  expect(rect).toEqual({ x: 500, y: 500, width: 1, height: 1 });
});

test("画像の右下の端を指すときも、画像の内側に 1 ピクセル残す", () => {
  const rect = cropRect({ width: 100, height: 100 }, { x: 1, y: 1, width: 0.2, height: 0.2 });

  expect(rect).toEqual({ x: 99, y: 99, width: 1, height: 1 });
});

test("全体を指す範囲は、画像と同じ大きさになる", () => {
  const rect = cropRect({ width: 640, height: 480 }, { x: 0, y: 0, width: 1, height: 1 });

  expect(rect).toEqual({ x: 0, y: 0, width: 640, height: 480 });
});
