// 授業のボードに置く図表 (仕様書 5.5、architecture 5.3)。教材の画像を取り、ボードの初期配置の材料にする
import { useQuery } from "@tanstack/react-query";
import type { BoardFigure } from "../../shared/board";
import { fetchMaterialImage, type LessonEntry } from "./lesson-api";

/**
 * 図表の画像を取る。取れなかった図表は外し、問題文と生徒のノートは残す (図表は補助の表示のため)。
 * 1 回の授業の中では、同じ画像を取り直さない
 */
export function useLessonFigures(entry: LessonEntry) {
  return useQuery({
    queryKey: ["lesson-figures", entry.lessonId],
    queryFn: () => loadFigures(entry),
    staleTime: Infinity,
  });
}

async function loadFigures(entry: LessonEntry): Promise<BoardFigure[]> {
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
