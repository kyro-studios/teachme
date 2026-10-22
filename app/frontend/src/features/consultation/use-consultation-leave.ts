// 相談の画面を離れたときの中断。図表の取得中や画面の読み込み中でも行えるよう、画面の入口で監視する
import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import {
  abandonConsultation,
  forgetConsultationEntry,
  type ConsultationEntry,
} from "./consultation-api";

/**
 * 相談の開始の結果を受け取ったら、画面を離れるときに相談を中断する。
 * 中断は `keepalive` で送り、開始の結果のキャッシュも捨てる。
 * 開発時の二重の実行では、直前の中断を取り消す
 */
export function useConsultationLeave(entry: ConsultationEntry | undefined) {
  const queryClient = useQueryClient();
  const leaveTimerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const consultationId = entry?.consultationId;
  const materialId = entry?.materialId;

  useEffect(() => {
    if (!consultationId || !materialId) {
      return;
    }
    clearTimeout(leaveTimerRef.current);
    const leave = () => {
      // 中断した相談の開始の結果は捨てる。戻る、進むで来ても、中断された相談は出さない
      forgetConsultationEntry(queryClient, materialId);
      void abandonConsultation(consultationId, { keepalive: true }).catch(() => undefined);
    };
    window.addEventListener("pagehide", leave);
    return () => {
      window.removeEventListener("pagehide", leave);
      leaveTimerRef.current = setTimeout(leave, 0);
    };
  }, [consultationId, materialId, queryClient]);
}
