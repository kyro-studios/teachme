import { useRef } from "react";
import { useNavigate } from "react-router";
import { ImageExportButtons } from "../../shared/image-export/ImageExportButtons";
import { Button } from "../../shared/ui/Button";
import { SummaryNoteView } from "../../shared/ui/SummaryNoteView";
import type { SummaryNote } from "./lesson-state";

export type LessonSummaryPanelProps = {
  /** まとめノート。作れたときだけ渡す */
  summary: SummaryNote | null;
  /** まとめの作成が失敗した (「やり直す」を出す) */
  failed: boolean;
  onRetry: () => void;
};

// チャイムの後のまとめ (仕様書 5.6、5.8)。まとめノートと、画像の保存、ホームへの戻りを出す。
// 作成中は何も出さず、失敗のときは「やり直す」を出す
export function LessonSummaryPanel({ summary, failed, onRetry }: LessonSummaryPanelProps) {
  const navigate = useNavigate();
  const noteRef = useRef<HTMLDivElement>(null);

  if (summary) {
    return (
      <div className="mx-auto grid w-full max-w-3xl gap-6">
        <div ref={noteRef}>
          <SummaryNoteView summaryText={summary.summaryText} quotes={summary.quotes} />
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <ImageExportButtons
            target={noteRef}
            fileName="teachme-summary-note.png"
            title="まとめノート"
          />
          <Button onClick={() => navigate("/home")}>ホームに戻る</Button>
        </div>
      </div>
    );
  }

  if (failed) {
    return (
      <div className="mx-auto flex w-full max-w-3xl">
        <Button onClick={onRetry}>やり直す</Button>
      </div>
    );
  }

  return null;
}
