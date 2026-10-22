// 授業の API (仕様書 5.5、architecture 2.2)。授業の開始の結果は、画面へ渡すために問い合わせのキャッシュに置く
import { queryOptions, useMutation, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../api/client";
import type { components } from "../../api/schema";
import { clockOffset } from "./lesson-timing";

export type LessonStart = components["schemas"]["LessonStart"];
export type LessonReply = components["schemas"]["LessonReply"];
export type UtteranceResult = components["schemas"]["UtteranceResult"];
export type MaterialSummary = components["schemas"]["MaterialSummary"];
export type Region = components["schemas"]["Region"];
export type LessonSummary = components["schemas"]["LessonSummary"];

/**
 * 授業の画面が受け取るもの。開始の結果に、次の 2 つを足す
 * - 図表の画像を取る教材の ID (開始の結果には教材の ID が無いため。`GET /materials/{id}/images/{position}` で使う)
 * - 開始の結果を受け取った時点の、サーバーの時計と端末の時計のずれ (画面の読み込みの時間を、期限に含めないため)
 */
export type LessonEntry = LessonStart & { materialId: string; clockOffset: number };

/** 授業の開始の結果のキャッシュのキー。授業の画面は、ここから受け取る */
export function lessonEntryKey(lessonId: string) {
  return ["lesson-entry", lessonId] as const;
}

/**
 * 中断した授業の開始の結果を、キャッシュから捨てる。戻る、進むで同じ授業へ来ても、
 * 中断された授業を表示しないようにする (LessonPage は、無ければ授業の開始の画面へ戻す)
 */
export function forgetLessonEntry(queryClient: QueryClient, lessonId: string): void {
  queryClient.removeQueries({ queryKey: lessonEntryKey(lessonId) });
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
      // 受け取った時点で、時計のずれを決める。あとから画面を読み込んでも、その時間は期限に響かない
      return { ...start, materialId, clockOffset: clockOffset(start.serverNow, Date.now()) };
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

/**
 * チャイムの後の、まとめノートの作成 (`POST /lessons/{id}/summary`)。
 * チャイムの時点のボードの画像を送る。失敗したときは、同じ画像で「やり直す」ができる
 */
export function createLessonSummary(lessonId: string, boardImage: Blob): Promise<LessonSummary> {
  const body = new FormData();
  body.append("boardImage", boardImage, "board.png");
  return apiFetch<LessonSummary>(`/lessons/${lessonId}/summary`, { method: "POST", body });
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
 * AI 生徒の発話の音声を、先に全部取る (NFR-101。応答の後の待ちと、ペンの待ちを重ねる)。
 * 返すのは再生に使う URL。取れなかったときは、音声の URL をそのまま返し、再生のときに失敗させる (字幕だけで続ける)
 */
export async function loadLessonAudio(lessonId: string, turnId: string): Promise<string> {
  const src = lessonAudioUrl(lessonId, turnId);
  try {
    const response = await fetch(src);
    if (!response.ok) {
      return src;
    }
    return URL.createObjectURL(await response.blob());
  } catch {
    return src;
  }
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
