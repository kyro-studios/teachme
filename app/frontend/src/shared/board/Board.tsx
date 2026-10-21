import { useCallback, useEffect, useImperativeHandle, useRef, useState, type Ref } from "react";
import { Tldraw, type Editor } from "tldraw";
import "tldraw/tldraw.css";
import {
  exportBoardPng,
  layoutInitialBoard,
  placeBoardWrite,
  placeStudentAnnotation,
} from "./board-shapes";
import { toFraction, type Point, type Region } from "./geometry";
import { PenTracker, type RecordingRange } from "./pen";
import type { BoardWrite, InitialContent, StudentAnnotation } from "./types";

export type { BoardFigure, BoardWrite, InitialContent, StudentAnnotation } from "./types";

/** 親の画面が、ボードに対して行う操作 */
export type BoardHandle = {
  /** ペン、指、マウスのどれかが触れている間 true */
  isWriting(): boolean;
  /** 最後にペンを離した時刻 (`Date.now()` と同じ基準)。まだ離していなければ null */
  lastReleasedAt(): number | null;
  /** 録音の範囲で触れた範囲 (最大 10 件)。割合で表す */
  pointedRegions(range: RecordingRange): Region[];
  /** 画面に見えている範囲を PNG にする。長辺は 1280 px 以下 */
  exportPng(): Promise<Blob>;
  /** AI 生徒の印を追加する */
  addStudentAnnotation(annotation: StudentAnnotation): void;
  /** 先輩先生の書き込みを追加する */
  addBoardWrites(writes: readonly BoardWrite[]): void;
};

export type BoardProps = InitialContent & {
  className?: string;
  ref?: Ref<BoardHandle>;
};

/**
 * ホワイトボード (tldraw) の部品。描画のツールで始め、tldraw のツールの UI は出さない (ペンの操作を妨げないため)。
 * ボードの上には、タップが要るボタンを置かない。
 *
 * 初期配置は、マウントのときの内容で一度だけ作る。あとから props を変えても、ボードは作り直さない。
 */
export function Board({ className = "", ref, ...content }: BoardProps) {
  const [initial] = useState<InitialContent>(() => content);
  const [tracker] = useState(() => new PenTracker());
  const frameRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<Editor | null>(null);

  useImperativeHandle(
    ref,
    () => ({
      isWriting: () => tracker.isWriting(),
      lastReleasedAt: () => tracker.lastReleasedAt(),
      pointedRegions: (range) => tracker.pointedRegions(range),
      exportPng: () => exportBoardPng(requireEditor(editorRef)),
      addStudentAnnotation: (annotation) => {
        placeStudentAnnotation(requireEditor(editorRef), annotation);
      },
      addBoardWrites: (writes) => {
        const editor = requireEditor(editorRef);
        for (const write of writes) {
          placeBoardWrite(editor, write);
        }
      },
    }),
    [tracker],
  );

  // 離した指やペンは、ボードの外で離しても拾う。取り消されたものも同じに扱う
  useEffect(() => {
    const release = (event: PointerEvent) => tracker.release(event.pointerId);
    const cancel = (event: PointerEvent) => tracker.cancel(event.pointerId);
    window.addEventListener("pointerup", release, true);
    window.addEventListener("pointercancel", cancel, true);
    return () => {
      window.removeEventListener("pointerup", release, true);
      window.removeEventListener("pointercancel", cancel, true);
    };
  }, [tracker]);

  const pointOf = (event: { clientX: number; clientY: number }): Point => {
    const frame = frameRef.current?.getBoundingClientRect();
    return frame ? toFraction(event.clientX, event.clientY, frame) : { x: 0, y: 0 };
  };

  const handleMount = useCallback(
    (editor: Editor) => {
      editorRef.current = editor;
      let active = true;
      layoutInitialBoard(editor, initial, () => active).catch(() => {
        // 図表の切り出しに失敗しても、問題文と生徒のノートは残す
        console.error("ボードの初期配置を完成できませんでした");
      });
      return () => {
        active = false;
        editorRef.current = null;
      };
    },
    [initial],
  );

  // capture で受けて、tldraw の内部の処理より先に、ペンの接触を拾う
  return (
    <div
      ref={frameRef}
      className={`relative h-full w-full touch-none ${className}`}
      onPointerDownCapture={(event) => tracker.press(event.pointerId, pointOf(event))}
      onPointerMoveCapture={(event) => tracker.move(event.pointerId, pointOf(event))}
    >
      <Tldraw hideUi initialState="draw" onMount={handleMount} />
    </div>
  );
}

function requireEditor(editorRef: { current: Editor | null }): Editor {
  if (!editorRef.current) {
    throw new Error("ボードの準備ができていません");
  }
  return editorRef.current;
}
