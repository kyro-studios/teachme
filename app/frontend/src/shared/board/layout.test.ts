import { expect, test } from "vitest";
import { fitWidth, stackFigures } from "./layout";

test("幅が上限を超える図表は、縦横の比を保って縮小する", () => {
  expect(fitWidth({ width: 960, height: 480 }, 480)).toEqual({ width: 480, height: 240 });
});

test("幅が上限以下の図表は、拡大しない", () => {
  expect(fitWidth({ width: 200, height: 100 }, 480)).toEqual({ width: 200, height: 100 });
});

test("図表は、問題文の下に上から順に、間を空けて縦に並べる", () => {
  const placements = stackFigures(
    [
      { width: 300, height: 100 },
      { width: 960, height: 480 },
    ],
    { x: 0, top: 400, maxWidth: 480, gap: 32 },
  );

  expect(placements).toEqual([
    { x: 0, y: 400, width: 300, height: 100 },
    { x: 0, y: 532, width: 480, height: 240 },
  ]);
});

test("図表が無いときは、空の配列を返す", () => {
  expect(stackFigures([], { x: 0, top: 0, maxWidth: 480, gap: 32 })).toEqual([]);
});
