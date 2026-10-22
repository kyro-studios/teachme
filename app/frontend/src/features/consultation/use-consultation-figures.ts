// 相談のボードに置く図表 (仕様書 5.4、architecture 5.3)。問題文の下に、教材の画像から切り出した図表を置く
import { useQuery } from "@tanstack/react-query";
import type { BoardFigure } from "../../shared/board";
import { fetchMaterialImage, type ConsultationEntry } from "./consultation-api";

/**
 * 図表の画像を取る。取れなかった図表は外し、問題文は残す (図表は補助の表示のため)。
 * 1 回の相談の中では、同じ画像を取り直さない
 */
export function useConsultationFigures(entry: ConsultationEntry) {
  return useQuery({
    queryKey: ["consultation-figures", entry.consultationId],
    queryFn: () => loadFigures(entry),
    staleTime: Infinity,
  });
}

async function loadFigures(entry: ConsultationEntry): Promise<BoardFigure[]> {
  const loaded = await Promise.all(
    entry.figures.map(async (figure): Promise<BoardFigure | null> => {
      try {
        const image = await fetchMaterialImage(entry.materialId, figure.imagePosition);
        return { image, region: figure.region };
      } catch {
        return null;
      }
    }),
  );
  return loaded.filter((figure): figure is BoardFigure => figure !== null);
}
