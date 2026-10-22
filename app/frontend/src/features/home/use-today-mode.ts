// 今日のモードの切り替え (PUT /today-mode)。切り替えた後のホームの表示内容を、そのまま画面に反映する (仕様書 5.2)
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../api/client";
import { homeQuery, type Home } from "../../api/queries";

export type TodayModeValue = "normal" | "self_study" | "homework_check";

export function useSetTodayMode() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (mode: TodayModeValue) =>
      apiFetch<Home>("/today-mode", { method: "PUT", body: { mode } }),
    onSuccess: (home) => {
      queryClient.setQueryData(homeQuery.queryKey, home);
    },
  });
}
