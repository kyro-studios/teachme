// 相談の API (仕様書 5.4、architecture 2.4)。相談の開始の結果は、画面へ渡すために問い合わせのキャッシュに、教材の識別子のキーで置く
import { useMutation, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../api/client";
import type { components } from "../../api/schema";
import { clockOffset } from "./consultation-timing";

export type ConsultationStart = components["schemas"]["ConsultationStart"];
export type ConsultationReply = components["schemas"]["ConsultationReply"];
export type UtteranceResult = components["schemas"]["UtteranceResult"];
export type Region = components["schemas"]["Region"];

/**
 * 相談の画面が受け取るもの。開始の結果に、次の 2 つを足す
 * - 図表の画像を取る教材の ID (開始の結果には教材の ID が無いため。`GET /materials/{id}/images/{position}` で使う)
 * - 開始の結果を受け取った時点の、サーバーの時計と端末の時計のずれ (画面の読み込みの時間を、期限に含めないため)
 */
export type ConsultationEntry = ConsultationStart & { materialId: string; clockOffset: number };

/** 相談の開始の結果のキャッシュのキー。相談の画面は、教材の識別子でここから受け取る */
export function consultationEntryKey(materialId: string) {
  return ["consultation-entry", materialId] as const;
}

/**
 * 中断した相談の開始の結果を、キャッシュから捨てる。戻る、進むで来ても、中断された相談を表示しないようにする
 * (ConsultationPage は、無ければ教材の詳細へ戻す)
 */
export function forgetConsultationEntry(queryClient: QueryClient, materialId: string): void {
  queryClient.removeQueries({ queryKey: consultationEntryKey(materialId) });
}

/** 相談を始める (`POST /consultations`)。成功したら、相談の画面が使えるように、結果をキャッシュに置く */
export function useStartConsultation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (materialId: string): Promise<ConsultationEntry> => {
      const start = await apiFetch<ConsultationStart>("/consultations", {
        method: "POST",
        body: { materialId },
      });
      // 受け取った時点で、時計のずれを決める。あとから画面を読み込んでも、その時間は期限に響かない
      return { ...start, materialId, clockOffset: clockOffset(start.serverNow, Date.now()) };
    },
    onSuccess: (entry) => {
      queryClient.setQueryData(consultationEntryKey(entry.materialId), entry);
    },
  });
}

/** 録音を文章にする (`POST /consultations/{id}/utterances`)。応答は作らない */
export function sendUtterance(consultationId: string, audio: Blob): Promise<UtteranceResult> {
  const body = new FormData();
  body.append("audio", audio, "recording");
  return apiFetch<UtteranceResult>(`/consultations/${consultationId}/utterances`, {
    method: "POST",
    body,
  });
}

/**
 * 認識できた発話に対する、先輩先生の応答 (`POST .../reply`)。
 * ボードの画像と、指している範囲は、録音を終えた時点のものを送る
 */
export function replyToMentor(
  consultationId: string,
  utteranceId: string,
  boardImage: Blob,
  pointedRegions: readonly Region[],
): Promise<ConsultationReply> {
  const body = new FormData();
  body.append("boardImage", boardImage, "board.png");
  body.append("pointedRegions", JSON.stringify(pointedRegions));
  return apiFetch<ConsultationReply>(
    `/consultations/${consultationId}/utterances/${utteranceId}/reply`,
    { method: "POST", body },
  );
}

/** 相談を中断する (`POST /consultations/{id}/abandon`)。画面を離れるときは、`keepalive` で送る */
export function abandonConsultation(consultationId: string, options: { keepalive?: boolean } = {}) {
  return apiFetch<void>(`/consultations/${consultationId}/abandon`, {
    method: "POST",
    keepalive: options.keepalive,
  });
}

/** 先輩先生の応答の音声の URL (`GET /consultations/{id}/turns/{turnId}/audio`)。応答ごとに 1 回だけ生成される */
export function consultationAudioUrl(consultationId: string, turnId: string): string {
  return `/api/consultations/${consultationId}/turns/${turnId}/audio`;
}

/**
 * 先輩先生の応答の音声を、先に取る (応答の後の待ちと、ペンの待ちを重ねる)。
 * 返すのは再生に使う URL。取れなかったときは、音声の URL をそのまま返し、再生のときに失敗させる (字幕だけで続ける)
 */
export async function loadConsultationAudio(
  consultationId: string,
  turnId: string,
): Promise<string> {
  const src = consultationAudioUrl(consultationId, turnId);
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
 * 授業の lesson-api と同じ。JSON ではないため、apiFetch は使わない
 */
export async function fetchMaterialImage(materialId: string, position: number): Promise<Blob> {
  const response = await fetch(`/api/materials/${materialId}/images/${position}`);
  if (!response.ok) {
    throw new Error(`教材の画像を取得できませんでした (${response.status})`);
  }
  return response.blob();
}
