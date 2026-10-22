// 宿題の写真の検査 (仕様書 5.2、9 節)。JPEG か PNG で、1 枚 10 MB 以下。サーバーも同じ検査をする

/** 1 枚の大きさの上限 (バイト)。サーバーの ImageFormat.MAX_BYTES と同じ */
export const HOMEWORK_PHOTO_MAX_BYTES = 10 * 1024 * 1024;

export const INVALID_PHOTO_MESSAGE = "画像は JPEG か PNG で、1 枚 10 MB 以下にしてください";

const ACCEPTED_TYPES: readonly string[] = ["image/jpeg", "image/png"];

/** 写真が使えないときは、画面に出す文言を返す。使えるときは null */
export function homeworkPhotoError(photo: Pick<File, "type" | "size">): string | null {
  if (!ACCEPTED_TYPES.includes(photo.type) || photo.size > HOMEWORK_PHOTO_MAX_BYTES) {
    return INVALID_PHOTO_MESSAGE;
  }
  return null;
}
