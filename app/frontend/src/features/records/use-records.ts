// 学習記録の API (仕様書 5.8、5.9)。記録は保存されておらず、サーバーが集計した結果を読む。削除は、単元の記録を消す
import { queryOptions, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../api/client";
import { homeQuery } from "../../api/queries";
import type { components, paths } from "../../api/schema";

export type RecordUnitsResponse =
  paths["/records/units"]["get"]["responses"][200]["content"]["application/json"];
export type RecordUnit = components["schemas"]["RecordUnit"];
export type LearningReport = components["schemas"]["LearningReport"];
export type SummaryNote = components["schemas"]["SummaryNote"];

/** 学習記録のキー。一覧を無効にすると、その下の単元の記録も取り直す */
const RECORDS_KEY = ["records"] as const;

export const recordUnitsQuery = queryOptions({
  queryKey: [...RECORDS_KEY, "units"],
  queryFn: () => apiFetch<RecordUnitsResponse>("/records/units"),
});

/** 単元の画面の内容 (まとめノート、模試の結果、学習レポート)。学習レポートの画面も、同じ内容を使う */
export function recordUnitQuery(unitId: string) {
  return queryOptions({
    queryKey: [...RECORDS_KEY, "units", unitId],
    queryFn: () => apiFetch<RecordUnit>(`/records/units/${unitId}`),
  });
}

export function summaryNoteQuery(recordId: string) {
  return queryOptions({
    queryKey: [...RECORDS_KEY, "notes", recordId],
    queryFn: () => apiFetch<SummaryNote>(`/records/notes/${recordId}`),
  });
}

/**
 * 単元の学習記録の削除 (DELETE /records/units/{id}、仕様書 5.9)。
 * 理解度と模試の機会が変わるため、ホームも取り直す。教材は残る
 */
export function useDeleteRecordUnit() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (unitId: string) =>
      apiFetch<void>(`/records/units/${unitId}`, { method: "DELETE" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: RECORDS_KEY });
      void queryClient.invalidateQueries({ queryKey: homeQuery.queryKey });
    },
  });
}
