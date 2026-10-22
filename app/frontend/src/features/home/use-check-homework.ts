// 宿題の写真の判定 (POST /homework-checks)。写真と判定の結果は保存しない (仕様書 5.2、10 節)
import { useMutation } from "@tanstack/react-query";
import { apiFetch } from "../../api/client";
import type { components } from "../../api/schema";

export type HomeworkResult = components["schemas"]["HomeworkResult"];

export function useCheckHomework() {
  return useMutation({
    mutationFn: (photo: File) => {
      const body = new FormData();
      body.append("photo", photo);
      // 確認のチェックを入れた後にだけ呼ぶ (NFR-301)。サーバーも、この値が true でなければ送信を拒む
      body.append("photoConfirmed", "true");
      return apiFetch<HomeworkResult>("/homework-checks", { method: "POST", body });
    },
  });
}
