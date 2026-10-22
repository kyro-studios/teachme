import { useQuery } from "@tanstack/react-query";
import { useRef } from "react";
import { useParams } from "react-router";
import { LearningReportView } from "../features/records/LearningReportView";
import { failureMessage } from "../features/records/record-text";
import { recordUnitQuery } from "../features/records/use-records";
import { ImageExportButtons } from "../shared/image-export/ImageExportButtons";
import { PageFrame } from "../shared/ui/PageFrame";

// 学習レポート (仕様書 5.8)。LLM は使わず、保存された記録から作った内容を出す。
// 授業が 1 回も無いときは、画像を保存を出さない
export function ReportPage() {
  const { unitId = "" } = useParams();
  const unit = useQuery(recordUnitQuery(unitId));
  const reportRef = useRef<HTMLDivElement>(null);

  if (unit.error) {
    return (
      <PageFrame title="学習レポート">
        <p role="alert" className="text-lg font-bold text-red-700">
          {failureMessage(unit.error)}
        </p>
      </PageFrame>
    );
  }
  if (!unit.data) {
    return null;
  }

  const { report } = unit.data;
  return (
    <PageFrame title={report.unitName}>
      <div ref={reportRef} className="mx-auto w-full max-w-3xl">
        <LearningReportView report={report} />
      </div>
      {report.lessonCount > 0 ? (
        <div className="mx-auto w-full max-w-3xl">
          <ImageExportButtons
            target={reportRef}
            fileName="teachme-report.png"
            title="学習レポート"
          />
        </div>
      ) : null}
    </PageFrame>
  );
}
