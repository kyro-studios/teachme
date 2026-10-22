// 模試に挑戦する (POST /mock-exams、仕様書 5.7)。成功したら、結果を画面へ渡すためにキャッシュに置き、
// 模試の機会が消えたため、ホームを読み直す。失敗したときは、ホームの機会の表示を読み直して、残っている機会を確かめる
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../api/client";
import { homeQuery, mockExamQuery, type MockExamResult } from "../../api/queries";

export function useTakeMockExam() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<MockExamResult>("/mock-exams", { method: "POST" }),
    onSuccess: (result) => {
      queryClient.setQueryData(mockExamQuery(result.id).queryKey, result);
      void queryClient.invalidateQueries({ queryKey: homeQuery.queryKey });
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: homeQuery.queryKey });
    },
  });
}
