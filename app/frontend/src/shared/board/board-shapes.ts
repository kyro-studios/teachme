// tldraw の図形を作る部分。ボードの初期配置、AI の書き込み、ボードの画像の書き出し
import { AssetRecordType, createShapeId, toRichText, type Editor, type TLShapeId } from "tldraw";
import { blobToDataUrl, cropFigure, type CroppedFigure } from "./figure";
import { fitLongSide, regionToFrame, type Region } from "./geometry";
import { stackFigures, type Placement } from "./layout";
import type { BoardWrite, InitialContent, StudentAnnotation } from "./types";

/** ボードの画像の長辺の上限 (ピクセル)。送る画像を小さくして、費用を抑える (architecture 5.3) */
export const BOARD_IMAGE_MAX_LONG_SIDE = 1280;

/** 問題の欄と、生徒のノートの欄の幅 (ボードの単位) */
const COLUMN_WIDTH = 480;
const COLUMN_GAP = 96;
const HEADING_GAP = 24;
const FIGURE_GAP = 32;

/** AI 生徒の指し示しの矢印の長さ */
const ARROW_LENGTH = 96;
/** 強調の枠の最小の大きさ。ペンで触れただけの範囲も見えるように */
const MIN_HIGHLIGHT_SIZE = 8;
/** 範囲の無い文字の書き込みを置く位置 (枠に対する割合)。下の方の左に置く */
const FALLBACK_TEXT_POSITION = { x: 0.05, y: 0.8 };

/** 画面に見えている範囲 (ページの単位)。割合の座標は、この枠に対して決める */
function viewportFrame(editor: Editor): Region {
  const bounds = editor.getViewportPageBounds();
  return { x: bounds.x, y: bounds.y, width: bounds.w, height: bounds.h };
}

function bottomOf(editor: Editor, id: TLShapeId): number {
  return editor.getShapePageBounds(id)?.maxY ?? 0;
}

/**
 * 初期配置を置く。「問題」の欄に問題文と、その下に図表を縦に並べ、「生徒のノート」の欄に途中の解答を置く。
 * 図表は、切り出してから置く。`isActive` が false になったら (画面を離れた後など) 置かない
 */
export async function layoutInitialBoard(
  editor: Editor,
  content: InitialContent,
  isActive: () => boolean,
): Promise<void> {
  // 文字の寸法を、フォントの読み込み後に測るため
  await editor.fonts.loadRequiredFontsForCurrentPage(editor.options.maxFontsToLoadBeforeRender);
  if (!isActive()) {
    return;
  }
  const problemBody = addColumn(editor, { x: 0, title: "問題", text: content.problemText });
  if (content.studentNote !== undefined) {
    addColumn(editor, {
      x: COLUMN_WIDTH + COLUMN_GAP,
      title: "生徒のノート",
      text: content.studentNote,
    });
  }

  const cropped = await Promise.all(
    content.figures.map((figure) => cropFigure(figure.image, figure.region)),
  );
  const sources = await Promise.all(cropped.map((figure) => blobToDataUrl(figure.blob)));
  if (!isActive()) {
    return;
  }
  const placements = stackFigures(cropped, {
    x: 0,
    top: bottomOf(editor, problemBody) + FIGURE_GAP,
    maxWidth: COLUMN_WIDTH,
    gap: FIGURE_GAP,
  });
  cropped.forEach((figure, index) => {
    addFigure(editor, sources[index], figure, placements[index]);
  });
}

function addColumn(editor: Editor, column: { x: number; title: string; text: string }): TLShapeId {
  const heading = createShapeId();
  editor.createShape({
    id: heading,
    type: "text",
    x: column.x,
    y: 0,
    props: { richText: toRichText(column.title), size: "l", font: "sans", autoSize: true },
  });

  const body = createShapeId();
  editor.createShape({
    id: body,
    type: "text",
    x: column.x,
    y: bottomOf(editor, heading) + HEADING_GAP,
    props: { richText: toRichText(column.text), w: COLUMN_WIDTH, autoSize: false },
  });
  return body;
}

function addFigure(editor: Editor, src: string, figure: CroppedFigure, placement: Placement): void {
  const assetId = AssetRecordType.createId();
  editor.createAssets([
    {
      id: assetId,
      typeName: "asset",
      type: "image",
      props: {
        name: "figure.png",
        src,
        w: figure.width,
        h: figure.height,
        mimeType: "image/png",
        isAnimated: false,
      },
      meta: {},
    },
  ]);
  editor.createShape({
    type: "image",
    x: placement.x,
    y: placement.y,
    props: {
      w: placement.width,
      h: placement.height,
      assetId,
      playing: true,
      altText: "図表",
    },
  });
}

/**
 * AI 生徒の印を置く。point は範囲の中心を指す矢印、question_mark は範囲の右上に「？」を置く。
 * 範囲は、画面に見えている範囲に対する割合
 */
export function placeStudentAnnotation(editor: Editor, annotation: StudentAnnotation): void {
  const box = regionToFrame(annotation.region, viewportFrame(editor));
  if (annotation.kind === "point") {
    const centerX = box.x + box.width / 2;
    const centerY = box.y + box.height / 2;
    editor.createShape({
      type: "arrow",
      x: centerX - ARROW_LENGTH,
      y: centerY - ARROW_LENGTH,
      props: {
        start: { x: 0, y: 0 },
        end: { x: ARROW_LENGTH, y: ARROW_LENGTH },
        color: "red",
        arrowheadEnd: "arrow",
      },
    });
    return;
  }
  editor.createShape({
    type: "text",
    x: box.x + box.width,
    y: box.y,
    props: { richText: toRichText("？"), color: "red", size: "xl" },
  });
}

/**
 * 先輩先生の書き込みを置く。
 * text は範囲の左上に置く (範囲が無ければ、画面の下の方の左に置く)。
 * highlight は範囲を囲む枠にする (範囲が無ければ何もしない)
 */
export function placeBoardWrite(editor: Editor, write: BoardWrite): void {
  const frame = viewportFrame(editor);
  if (write.kind === "highlight") {
    if (!write.region) {
      return;
    }
    const box = regionToFrame(write.region, frame);
    editor.createShape({
      type: "geo",
      x: box.x,
      y: box.y,
      props: {
        geo: "rectangle",
        w: Math.max(box.width, MIN_HIGHLIGHT_SIZE),
        h: Math.max(box.height, MIN_HIGHLIGHT_SIZE),
        color: "orange",
        fill: "none",
        dash: "dashed",
      },
    });
    return;
  }
  if (!write.text) {
    return;
  }
  const at = write.region
    ? regionToFrame(write.region, frame)
    : regionToFrame({ ...FALLBACK_TEXT_POSITION, width: 0, height: 0 }, frame);
  editor.createShape({
    type: "text",
    x: at.x,
    y: at.y,
    props: { richText: toRichText(write.text), color: "blue" },
  });
}

/**
 * 画面に見えている範囲を、PNG にする。長辺は `BOARD_IMAGE_MAX_LONG_SIDE` 以下に縮小する。
 * 指している範囲の割合は、この画像と同じ枠で決まる
 */
export async function exportBoardPng(editor: Editor): Promise<Blob> {
  const frame = viewportFrame(editor);
  const scale = fitLongSide(frame.width, frame.height, BOARD_IMAGE_MAX_LONG_SIDE);
  const { blob } = await editor.toImage([...editor.getCurrentPageShapeIds()], {
    format: "png",
    bounds: editor.getViewportPageBounds(),
    scale,
    pixelRatio: 1,
    padding: 0,
    background: true,
  });
  return blob;
}
