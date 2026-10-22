// 教材の画面に出す文言。API の値 (英字) を、仕様書 10 節の表示名にする
import type { components } from "../../api/schema";
import { ApiError } from "../../api/client";

export type MaterialStatus = components["schemas"]["MaterialStatus"];

/** 教材の状態の表示名 (仕様書 5.3、10 節)。キーは API の MaterialStatus と同じ値 */
export const MATERIAL_STATUS_LABELS: Readonly<Record<MaterialStatus, string>> = {
  organizing: "整理中",
  organized: "整理済み",
  failed: "整理できませんでした",
};

/** 通信や API の失敗を、画面に出す文言にする。API が文言を返したときは、その文言を使う */
export function failureMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : "サーバーとの通信に失敗しました";
}
