import { useEffect, useState } from "react";
import type { components } from "../../api/schema";
// tldraw を読み込まない切り出し関数だけを読む (索引は Board を含み、画面の単体テストで tldraw が動かないため)
import { cropFigure } from "../../shared/board/figure";

type Figure = components["schemas"]["Figure"];

/** 教材の画像を、登録したときのバイト列のまま取る (GET /materials/{id}/images/{position})。JSON ではないため、apiFetch は使わない */
async function fetchMaterialImage(materialId: string, position: number): Promise<Blob> {
  const response = await fetch(`/api/materials/${materialId}/images/${position}`);
  if (!response.ok) {
    throw new Error(`教材の画像を取得できませんでした (${response.status})`);
  }
  return response.blob();
}

/** 問題の図表 (仕様書 5.3)。教材の画像から、矩形の部分を切り出して表示する。出せないときは何も出さず、問題文は残す */
export function MaterialFigure({
  materialId,
  figure,
  index,
}: {
  materialId: string;
  figure: Figure;
  /** 問題の中での、図表の番号 (1 から)。文字による代わりに使う */
  index: number;
}) {
  const [src, setSrc] = useState<string | null>(null);
  const { imagePosition, region } = figure;

  useEffect(() => {
    let active = true;
    let objectUrl: string | null = null;
    void (async () => {
      try {
        const image = await fetchMaterialImage(materialId, imagePosition);
        const cropped = await cropFigure(image, region);
        if (active) {
          objectUrl = URL.createObjectURL(cropped.blob);
          setSrc(objectUrl);
        }
      } catch {
        // 図表は補助の表示なので、失敗しても問題文と他の見出しは出し続ける
      }
    })();
    return () => {
      active = false;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [materialId, imagePosition, region]);

  if (!src) {
    return null;
  }
  return (
    <img src={src} alt={`問題の図表 ${index}`} className="max-h-96 w-fit max-w-full rounded-md" />
  );
}
