import { useRef, useState } from "react";
import { Board, type BoardHandle } from "./Board";

// shared/board の利用の例。経路には置かず、この部品の動作を手で確かめるために使う。
// 操作は、ボードの外に置く (ボードの上に、タップが要るボタンを重ねない)
const sample = {
  problemText: "次の方程式を解きなさい。\n2x + 3 = 11",
  studentNote: "2x = 11 - 3",
  figures: [],
};

const buttonClass =
  "min-h-11 min-w-11 rounded-lg border-2 border-slate-500 bg-white px-5 font-bold text-slate-900";

export function BoardExample() {
  const board = useRef<BoardHandle>(null);
  const [message, setMessage] = useState("");

  const exportImage = async () => {
    if (!board.current) {
      return;
    }
    try {
      const blob = await board.current.exportPng();
      setMessage(`画像を書き出しました (${Math.round(blob.size / 1024)} KB)`);
    } catch {
      setMessage("画像を書き出せませんでした");
    }
  };

  const addAiWrites = () => {
    board.current?.addStudentAnnotation({
      kind: "point",
      region: { x: 0.1, y: 0.1, width: 0.3, height: 0.1 },
    });
    board.current?.addBoardWrites([
      { kind: "highlight", region: { x: 0.05, y: 0.05, width: 0.4, height: 0.2 } },
      { kind: "text", text: "2x = 8" },
    ]);
  };

  return (
    <section className="flex flex-col gap-4 p-4">
      <div className="h-[60vh] rounded-lg border border-slate-400">
        <Board ref={board} {...sample} />
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <button type="button" className={buttonClass} onClick={exportImage}>
          画像を書き出す
        </button>
        <button type="button" className={buttonClass} onClick={addAiWrites}>
          AI の書き込みを試す
        </button>
        <p role="status">{message}</p>
      </div>
    </section>
  );
}
