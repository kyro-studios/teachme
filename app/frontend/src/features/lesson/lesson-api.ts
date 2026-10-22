// 授業の API (仕様書 5.5、architecture 2.2)。授業の開始の結果は、画面へ渡すために問い合わせのキャッシュに置く
import { queryOptions, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../api/client";
import type { components } from "../../api/schema";

export type LessonStart = components["schemas"]["LessonStart"];
export type LessonReply = components["schemas"]["LessonReply"];
export type UtteranceResult = components["schemas"]["UtteranceResult"];
export type MaterialSummary = components["schemas"]["MaterialSummary"];
export type Region = components["schemas"]["Region"];

/**
 * 授業の画面が受け取るもの。開始の結果に、図表の画像を取る教材の ID を足す
 * (開始の結果には教材の ID が無いため。`GET /materials/{id}/images/{position}` で使う)
 */
export type LessonEntry = LessonStart & { materialId: string };

/** 授業の開始の結果のキャッシュのキー。授業の画面は、ここから受け取る */
export function lessonEntryKey(lessonId: string) {
  return ["lesson-entry", lessonId] as const;
}

/** 授業に使える教材 (整理済み) の一覧。`GET /materials` のうち、`organized` だけ */
export const organizedMaterialsQuery = queryOptions({
  queryKey: ["lesson-materials"],
  queryFn: async () => {
    const { materials } = await apiFetch<{ materials: MaterialSummary[] }>("/materials");
    return materials.filter((material) => material.status === "organized");
  },
});

/** 授業を始める (`POST /lessons`)。成功したら、授業の画面が使えるように、結果をキャッシュに置く */
export function useStartLesson() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (materialId: string): Promise<LessonEntry> => {
      const start = await apiFetch<LessonStart>("/lessons", {
        method: "POST",
        body: { materialId },
      });
      return { ...start, materialId };
    },
    onSuccess: (entry) => {
      queryClient.setQueryData(lessonEntryKey(entry.lessonId), entry);
    },
  });
}

/** 録音を文章にする (`POST /lessons/{id}/utterances`)。応答は作らない */
export function sendUtterance(lessonId: string, audio: Blob): Promise<UtteranceResult> {
  const body = new FormData();
  body.append("audio", audio, "recording");
  return apiFetch<UtteranceResult>(`/lessons/${lessonId}/utterances`, { method: "POST", body });
}

/**
 * 認識できた発話に対する、AI 生徒の応答 (`POST .../reply`)。
 * ボードの画像と、指している範囲は、録音を終えた時点のものを送る
 */
export function replyToUtterance(
  lessonId: string,
  utteranceId: string,
  boardImage: Blob,
  pointedRegions: readonly Region[],
): Promise<LessonReply> {
  const body = new FormData();
  body.append("boardImage", boardImage, "board.png");
  body.append("pointedRegions", JSON.stringify(pointedRegions));
  return apiFetch<LessonReply>(`/lessons/${lessonId}/utterances/${utteranceId}/reply`, {
    method: "POST",
    body,
  });
}

/** 授業を中断する (`POST /lessons/{id}/abandon`)。画面を離れるときは、`keepalive` で送る */
export function abandonLesson(lessonId: string, options: { keepalive?: boolean } = {}) {
  return apiFetch<void>(`/lessons/${lessonId}/abandon`, {
    method: "POST",
    keepalive: options.keepalive,
  });
}

/** AI 生徒の発話の音声の URL (`GET /lessons/{id}/turns/{turnId}/audio`)。発話ごとに 1 回だけ生成される */
export function lessonAudioUrl(lessonId: string, turnId: string): string {
  return `/api/lessons/${lessonId}/turns/${turnId}/audio`;
}

/**
 * 教材の画像を、登録したときのバイト列のまま取る (`GET /materials/{id}/images/{position}`)。
 * JSON ではないため、apiFetch は使わない
 */
export async function fetchMaterialImage(materialId: string, position: number): Promise<Blob> {
  const response = await fetch(`/api/materials/${materialId}/images/${position}`);
  if (!response.ok) {
    throw new Error(`教材の画像を取得できませんでした (${response.status})`);
  }
  return response.blob();
}
