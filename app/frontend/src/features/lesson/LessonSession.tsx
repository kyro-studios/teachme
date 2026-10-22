import { useRef } from "react";
import { Avatar } from "../../shared/avatar";
import { Board, type BoardFigure, type BoardHandle } from "../../shared/board";
import { Button } from "../../shared/ui/Button";
import type { LessonEntry } from "./lesson-api";
import { type Speaker } from "./lesson-state";
import { LessonSummaryPanel } from "./LessonSummaryPanel";
import { useLessonSession } from "./use-lesson-session";

/** 授業の画面の注意 (仕様書 5.5、10 節) */
const PRIVACY_NOTICE = "氏名、学校名、連絡先は話さないでください";

/** 字幕の表示名 (仕様書 10 節) */
const SPEAKER_LABELS: Readonly<Record<Speaker, string>> = {
  you: "あなた",
  student: "生徒",
};

export type LessonSessionProps = {
  entry: LessonEntry;
  /** ボードに置く図表。画像の取得が済んでから渡す (ボードは、最初の内容で一度だけ作る) */
  figures: BoardFigure[];
};

// 授業の画面 (仕様書 5.5、architecture 5.4)。左にボード、右にキャラクターと字幕と「話す」を置く。
// チャイムの後は、まとめノートに替える。ボードは、画像を取った後も残す (隠すだけで、作り直さない)
export function LessonSession({ entry, figures }: LessonSessionProps) {
  const boardRef = useRef<BoardHandle>(null);
  const { state, pressTalk, pressRetrySummary } = useLessonSession(entry, boardRef);

  const recording = state.phase === "recording";
  const canTalk = state.phase === "idle" || recording;
  const inSummary =
    state.phase === "summarizing" ||
    state.phase === "summaryFailed" ||
    state.phase === "summarized";

  return (
    <main className="flex h-dvh flex-col gap-4 px-6 py-4">
      <header className="flex flex-wrap items-baseline justify-between gap-4">
        <h1 className="text-2xl font-bold text-slate-900">授業</h1>
        <p className="text-lg text-slate-900">{PRIVACY_NOTICE}</p>
      </header>

      {state.notice ? (
        <p role="alert" className="text-lg font-bold text-red-700">
          {state.notice}
        </p>
      ) : null}

      <div className={`min-h-0 flex-1 gap-6 ${inSummary ? "hidden" : "flex"}`}>
        <section
          aria-label="ボード"
          className="min-w-0 flex-1 overflow-hidden rounded-xl border-2 border-slate-400 bg-white"
        >
          <Board
            ref={boardRef}
            problemText={entry.problemText}
            studentNote={entry.studentWork}
            figures={figures}
          />
        </section>

        <aside className="flex w-80 shrink-0 flex-col gap-3">
          <Avatar
            character={entry.student.id}
            expression={state.expression}
            gesture={state.gesture}
            className="mx-auto max-w-56"
          />
          {state.filler ? (
            <p role="status" className="text-lg font-bold text-slate-700">
              {state.filler}
            </p>
          ) : null}
          <ol aria-label="字幕" className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto">
            {state.subtitles.map((subtitle, index) => (
              <li key={index} className="rounded-lg bg-slate-100 px-4 py-2">
                <span className="block text-sm font-bold text-slate-600">
                  {SPEAKER_LABELS[subtitle.speaker]}
                </span>
                <p className="text-lg leading-snug text-slate-900">{subtitle.text}</p>
              </li>
            ))}
          </ol>
          <Button className="w-full" disabled={!canTalk} onClick={pressTalk}>
            {recording ? "話し終える" : "話す"}
          </Button>
        </aside>
      </div>

      {inSummary ? (
        <div className="min-h-0 flex-1 overflow-y-auto">
          <LessonSummaryPanel
            summary={state.phase === "summarized" ? state.summary : null}
            failed={state.phase === "summaryFailed"}
            onRetry={pressRetrySummary}
          />
        </div>
      ) : null}
    </main>
  );
}
