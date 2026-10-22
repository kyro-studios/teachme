import { useQuery } from "@tanstack/react-query";
import { useRef } from "react";
import { useParams } from "react-router";
import { failureMessage } from "../features/records/record-text";
import { summaryNoteQuery } from "../features/records/use-records";
import { ImageExportButtons } from "../shared/image-export/ImageExportButtons";
import { PageFrame } from "../shared/ui/PageFrame";
import { SummaryNoteView } from "../shared/ui/SummaryNoteView";

// 記録されたまとめノート (仕様書 5.6、5.8)。授業の直後と同じ部品で表示し、「画像を保存」を置く
export function SummaryNotePage() {
  const { recordId = "" } = useParams();
  const note = useQuery(summaryNoteQuery(recordId));
  const noteRef = useRef<HTMLDivElement>(null);

  if (note.error) {
    return (
      <PageFrame title="まとめノート">
        <p role="alert" className="text-lg font-bold text-red-700">
          {failureMessage(note.error)}
        </p>
      </PageFrame>
    );
  }
  if (!note.data) {
    return null;
  }

  return (
    <PageFrame title={note.data.unitName}>
      <div ref={noteRef} className="mx-auto w-full max-w-3xl">
        <SummaryNoteView summaryText={note.data.summaryText} quotes={note.data.quotes} />
      </div>
      <div className="mx-auto w-full max-w-3xl">
        <ImageExportButtons
          target={noteRef}
          fileName="teachme-summary-note.png"
          title="まとめノート"
        />
      </div>
    </PageFrame>
  );
}
