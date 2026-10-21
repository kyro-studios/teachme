// ボードが受け取る、AI 生徒と先輩先生の書き込みと、初期配置の型。
// 形は docs/api/openapi.yaml の StudentAnnotation、BoardWrite、Figure に合わせる (shared は api に依存しない)
import type { Region } from "./geometry";

/** AI 生徒がボードに付ける印。point は指し示し、question_mark は「？」の印 */
export type StudentAnnotation = {
  kind: "point" | "question_mark";
  region: Region;
};

/** 先輩先生がボードに書き込むもの。text は文字 (式を含む)、highlight は範囲の強調 */
export type BoardWrite = {
  kind: "text" | "highlight";
  text?: string;
  region?: Region;
};

/** 問題文の下に置く図表。教材の画像 (Blob) と、画像の中の範囲 */
export type BoardFigure = {
  image: Blob;
  region: Region;
};

/**
 * ボードの初期配置の内容。`studentNote` を省略すると、「生徒のノート」の欄は置かない
 * (相談のボードは問題文と図表だけ。仕様書 5.4)
 */
export type InitialContent = {
  problemText: string;
  studentNote?: string;
  figures: readonly BoardFigure[];
};
