import { describe, expect, test } from "vitest";
import {
  MATERIAL_IMAGE_FORMAT_MESSAGE,
  MATERIAL_IMAGES_COUNT_MESSAGE,
  MATERIAL_IMAGE_MAX_BYTES,
  materialImagesError,
} from "./material-images";

const jpeg = { type: "image/jpeg", size: 1024 };

describe("materialImagesError (教材の画像の検査)", () => {
  test("JPEG か PNG の画像を 1 枚から 3 枚まで選ぶと、問題は無い", () => {
    expect(materialImagesError([jpeg])).toBeNull();
    expect(materialImagesError([jpeg, { type: "image/png", size: 1 }, jpeg])).toBeNull();
  });

  test("0 枚と 4 枚以上は、枚数の文言を返す", () => {
    expect(materialImagesError([])).toBe(MATERIAL_IMAGES_COUNT_MESSAGE);
    expect(materialImagesError([jpeg, jpeg, jpeg, jpeg])).toBe(MATERIAL_IMAGES_COUNT_MESSAGE);
  });

  test("JPEG か PNG でない画像が 1 枚でもあれば、形式の文言を返す", () => {
    expect(materialImagesError([{ type: "image/gif", size: 1 }])).toBe(
      MATERIAL_IMAGE_FORMAT_MESSAGE,
    );
    expect(materialImagesError([jpeg, { type: "image/webp", size: 1 }])).toBe(
      MATERIAL_IMAGE_FORMAT_MESSAGE,
    );
  });

  test("1 枚が 10 MB を超えると、形式の文言を返す。ちょうど 10 MB は使える", () => {
    expect(materialImagesError([{ type: "image/jpeg", size: MATERIAL_IMAGE_MAX_BYTES + 1 }])).toBe(
      MATERIAL_IMAGE_FORMAT_MESSAGE,
    );
    expect(
      materialImagesError([{ type: "image/jpeg", size: MATERIAL_IMAGE_MAX_BYTES }]),
    ).toBeNull();
  });
});
