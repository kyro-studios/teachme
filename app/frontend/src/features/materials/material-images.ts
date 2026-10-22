// 教材の画像の検査 (仕様書 5.3、9 節)。1 枚から 3 枚で、JPEG か PNG、1 枚 10 MB 以下。サーバーも同じ検査をする

/** 教材の画像の枚数の上限。下限は 1 枚 */
export const MATERIAL_IMAGES_MAX_COUNT = 3;

/** 1 枚の大きさの上限 (バイト)。サーバーの ImageFormat.MAX_BYTES と同じ */
export const MATERIAL_IMAGE_MAX_BYTES = 10 * 1024 * 1024;

export const MATERIAL_IMAGES_COUNT_MESSAGE = "画像は 1 枚から 3 枚まで選んでください";

export const MATERIAL_IMAGE_FORMAT_MESSAGE = "画像は JPEG か PNG で、1 枚 10 MB 以下にしてください";

const ACCEPTED_TYPES: readonly string[] = ["image/jpeg", "image/png"];

/** 選んだ画像が使えないときは、画面に出す文言を返す。使えるときは null */
export function materialImagesError(images: readonly Pick<File, "type" | "size">[]): string | null {
  if (images.length < 1 || images.length > MATERIAL_IMAGES_MAX_COUNT) {
    return MATERIAL_IMAGES_COUNT_MESSAGE;
  }
  const invalid = images.some(
    (image) => !ACCEPTED_TYPES.includes(image.type) || image.size > MATERIAL_IMAGE_MAX_BYTES,
  );
  return invalid ? MATERIAL_IMAGE_FORMAT_MESSAGE : null;
}
