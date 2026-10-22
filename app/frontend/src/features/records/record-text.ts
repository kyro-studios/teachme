// 学習記録の画面に出す文言。API が文言を返したときは、その文言を使う
import { ApiError } from "../../api/client";

/** 通信や API の失敗を、画面に出す文言にする */
export function failureMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : "サーバーとの通信に失敗しました";
}
