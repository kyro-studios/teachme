// 設定の保存 (PUT /settings)。保存に成功したら、入口の判定と画面が読む設定を、保存した内容に差し替える
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../api/client";
import { homeQuery, settingsQuery, type SettingsResponse } from "../../api/queries";
import type { SettingsInput } from "./setup-validation";

export function useSaveSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: SettingsInput) =>
      apiFetch<SettingsResponse>("/settings", { method: "PUT", body: input }),
    onSuccess: (saved) => {
      queryClient.setQueryData(settingsQuery.queryKey, saved);
      // ホームの表示 (担当する生徒、教科と単元名) は、変わった設定で取り直す
      void queryClient.invalidateQueries({ queryKey: homeQuery.queryKey });
    },
  });
}
