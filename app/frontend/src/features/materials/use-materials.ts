// 教材の API (仕様書 5.3、5.9)。整理が終わるまで、一覧と詳細を 2 秒ごとに取り直す
import { queryOptions, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../api/client";
import type { components } from "../../api/schema";

export type MaterialSummary = components["schemas"]["MaterialSummary"];
export type MaterialDetail = components["schemas"]["MaterialDetail"];

/** 整理の結果を待つ間の、取り直しの間隔 (ミリ秒) */
export const ORGANIZING_POLL_INTERVAL_MS = 2_000;

/** 教材の一覧と詳細のキー。一覧を無効にすると、その下の詳細も取り直す */
const MATERIALS_KEY = ["materials"] as const;

export const materialsQuery = queryOptions({
  queryKey: MATERIALS_KEY,
  queryFn: () => apiFetch<{ materials: MaterialSummary[] }>("/materials"),
  refetchInterval: (query) =>
    query.state.data?.materials.some((material) => material.status === "organizing")
      ? ORGANIZING_POLL_INTERVAL_MS
      : false,
});

export function materialQuery(materialId: string) {
  return queryOptions({
    queryKey: [...MATERIALS_KEY, materialId],
    queryFn: () => apiFetch<MaterialDetail>(`/materials/${materialId}`),
    refetchInterval: (query) =>
      query.state.data?.status === "organizing" ? ORGANIZING_POLL_INTERVAL_MS : false,
  });
}

export function useMaterials() {
  return useQuery(materialsQuery);
}

export function useMaterial(materialId: string) {
  return useQuery(materialQuery(materialId));
}

/** 教材の登録 (POST /materials)。画像と確認のチェックを送り、整理は非同期で始まる */
export function useCreateMaterial() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (images: File[]) => {
      const body = new FormData();
      for (const image of images) {
        body.append("images", image);
      }
      // 確認のチェックを入れた後にだけ呼ぶ (NFR-301)。サーバーも、この値が true でなければ送信を拒む
      body.append("photoConfirmed", "true");
      return apiFetch<MaterialSummary>("/materials", { method: "POST", body });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: MATERIALS_KEY });
    },
  });
}

/** 整理に失敗した教材の、整理のやり直し (POST /materials/{id}/retry)。「やり直す」に使う */
export function useRetryMaterial() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (materialId: string) =>
      apiFetch<MaterialSummary>(`/materials/${materialId}/retry`, { method: "POST" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: MATERIALS_KEY });
    },
  });
}

/** 教材と整理の結果の削除 (DELETE /materials/{id}、仕様書 5.9)。削除したら、一覧を取り直す */
export function useDeleteMaterial() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (materialId: string) =>
      apiFetch<void>(`/materials/${materialId}`, { method: "DELETE" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: MATERIALS_KEY });
    },
  });
}
