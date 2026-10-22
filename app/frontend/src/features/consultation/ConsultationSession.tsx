import { useRef } from "react";
import { useNavigate } from "react-router";
import { Avatar } from "../../shared/avatar";
import { Board, type BoardFigure, type BoardHandle } from "../../shared/board";
import { Button } from "../../shared/ui/Button";
import type { ConsultationEntry } from "./consultation-api";
import { type Speaker } from "./consultation-state";
import { useConsultationSession } from "./use-consultation-session";

/** 相談の画面の注意 (仕様書 5.4、10 節) */
const PRIVACY_NOTICE = "氏名、学校名、連絡先は話さないでください";

/** 字幕の表示名 (仕様書 10 節) */
const SPEAKER_LABELS: Readonly<Record<Speaker, string>> = {
  you: "あなた",
  mentor: "先輩先生",
};

export type ConsultationSessionProps = {
  entry: ConsultationEntry;
  /** ボードに置く図表。画像の取得が済んでから渡す (ボードは、最初の内容で一度だけ作る) */
  figures: BoardFigure[];
};

// 先輩先生との相談の画面 (仕様書 5.4、architecture 5.4)。左にボード、右に先輩先生と字幕と「話す」を置く
export function ConsultationSession({ entry, figures }: ConsultationSessionProps) {
  const navigate = useNavigate();
  const boardRef = useRef<BoardHandle>(null);
  const { state, pressTalk } = useConsultationSession(entry, boardRef);

  const recording = state.phase === "recording";
  const canTalk = state.phase === "idle" || recording;

  return (
    <main className="flex h-dvh flex-col gap-4 px-6 py-4">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-slate-900">先輩先生との相談</h1>
        <p className="text-lg text-slate-900">{PRIVACY_NOTICE}</p>
        <Button variant="secondary" onClick={() => navigate(`/materials/${entry.materialId}`)}>
          戻る
        </Button>
      </header>

      <div className="flex min-h-0 flex-1 gap-6">
        <section
          aria-label="ボード"
          className="min-w-0 flex-1 overflow-hidden rounded-xl border-2 border-slate-400 bg-white"
        >
          <Board ref={boardRef} problemText={entry.problemText} figures={figures} />
        </section>

        <aside className="flex w-80 shrink-0 flex-col gap-3">
          <Avatar
            character="mentor"
            expression={state.expression}
            gesture={state.gesture}
            className="mx-auto max-w-56"
          />
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
          {state.notice ? (
            <p role="alert" className="text-lg font-bold text-red-700">
              {state.notice}
            </p>
          ) : null}
          <Button className="w-full" disabled={!canTalk} onClick={pressTalk}>
            {recording ? "話し終える" : "話す"}
          </Button>
        </aside>
      </div>
    </main>
  );
}
