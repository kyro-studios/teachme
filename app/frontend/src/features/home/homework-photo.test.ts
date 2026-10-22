import { describe, expect, test } from "vitest";
import {
  HOMEWORK_PHOTO_MAX_BYTES,
  INVALID_PHOTO_MESSAGE,
  homeworkPhotoError,
} from "./homework-photo";

describe("homeworkPhotoError", () => {
  test("JPEG と PNG で、10 MB 以下の写真は使える", () => {
    expect(homeworkPhotoError({ type: "image/jpeg", size: 1024 })).toBeNull();
    expect(homeworkPhotoError({ type: "image/png", size: HOMEWORK_PHOTO_MAX_BYTES })).toBeNull();
  });

  test("JPEG か PNG 以外の形式と、10 MB を超える写真は、文言を返す", () => {
    expect(homeworkPhotoError({ type: "image/gif", size: 1024 })).toBe(INVALID_PHOTO_MESSAGE);
    expect(homeworkPhotoError({ type: "image/jpeg", size: HOMEWORK_PHOTO_MAX_BYTES + 1 })).toBe(
      INVALID_PHOTO_MESSAGE,
    );
  });
});
