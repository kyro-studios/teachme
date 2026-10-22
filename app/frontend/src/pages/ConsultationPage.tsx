import { skipToken, useQuery } from "@tanstack/react-query";
import { lazy, Suspense } from "react";
import { Navigate, useParams } from "react-router";
import {
  consultationEntryKey,
  type ConsultationEntry,
} from "../features/consultation/consultation-api";
import { useConsultationFigures } from "../features/consultation/use-consultation-figures";

// ボード (tldraw) を含むため、相談の画面は開いたときに読み込む。画面の入口の読み込みを軽くするため
const ConsultationSession = lazy(() =>
  import("../features/consultation/ConsultationSession").then((module) => ({
    default: module.ConsultationSession,
  })),
);

// 先輩先生との相談の画面 (仕様書 5.4)。開始の結果は、「先輩先生に相談する」で受け取ったものを使う。
// リロードすると、サーバーでは中断された後のため、教材の詳細へ戻す
export function ConsultationPage() {
  const { materialId = "" } = useParams();
  const entry = useQuery<ConsultationEntry>({
    queryKey: consultationEntryKey(materialId),
    queryFn: skipToken,
  });

  if (!entry.data) {
    return <Navigate to={`/materials/${materialId}`} replace />;
  }
  return <ConsultationWithFigures entry={entry.data} />;
}

// 図表の画像が揃ってから、相談の画面を作る (ボードは最初の内容で一度だけ作るため)
function ConsultationWithFigures({ entry }: { entry: ConsultationEntry }) {
  const figures = useConsultationFigures(entry);
  if (!figures.data) {
    return null;
  }
  return (
    <Suspense fallback={null}>
      <ConsultationSession entry={entry} figures={figures.data} />
    </Suspense>
  );
}
