import { expect, test } from "vitest";
import { AVATAR_ASSETS } from "./avatar-config";
import {
  AVATAR_CHARACTERS,
  AVATAR_EXPRESSIONS,
  AVATAR_GESTURES,
  CHARACTER_LABELS,
  EXPRESSION_LABELS,
} from "./avatar-model";

test("表情の表示名は、仕様書 10 節のとおり", () => {
  expect(AVATAR_EXPRESSIONS.map((expression) => EXPRESSION_LABELS[expression])).toEqual([
    "通常",
    "困り",
    "納得",
    "考え中",
  ]);
});

test("話し手の表示名は、生徒の 3 種類と先輩先生", () => {
  expect(AVATAR_CHARACTERS.map((character) => CHARACTER_LABELS[character])).toEqual([
    "直感・感覚派",
    "真面目な暗記派",
    "論理・ツッコミ派",
    "先輩先生",
  ]);
});

test("話し手ごとのモデルのパスは、互いに違い、model3.json を指す", () => {
  const urls = AVATAR_CHARACTERS.map((character) => AVATAR_ASSETS.modelUrls[character]);

  expect(new Set(urls).size).toBe(AVATAR_CHARACTERS.length);
  for (const url of urls) {
    expect(url.endsWith("/model3.json")).toBe(true);
  }
});

test("表情と身振りの、モデル上の名前は、すべて空でなく、種類の中で重ならない", () => {
  const expressionNames = AVATAR_EXPRESSIONS.map((expression) => {
    return AVATAR_ASSETS.expressionNames[expression];
  });
  const motionGroups = AVATAR_GESTURES.map((gesture) => AVATAR_ASSETS.motionGroups[gesture]);

  for (const name of [...expressionNames, ...motionGroups]) {
    expect(name.length).toBeGreaterThan(0);
  }
  expect(new Set(expressionNames).size).toBe(AVATAR_EXPRESSIONS.length);
  expect(new Set(motionGroups).size).toBe(AVATAR_GESTURES.length);
});
