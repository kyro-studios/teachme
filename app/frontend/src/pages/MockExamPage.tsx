import { useQuery } from "@tanstack/react-query";
import { useRef } from "react";
import { Navigate, useParams } from "react-router";
import { mockExamQuery } from "../api/queries";
import { MockExamResultView } from "../features/mock-exam/MockExamResultView";
import { ImageExportButtons } from "../shared/image-export/ImageExportButtons";
import { PageFrame } from "../shared/ui/PageFrame";

// 模試の結果の画面 (仕様書 5.7、5.8)。「画像を保存」は、結果の部分だけを PNG にする
export function MockExamPage() {
  const { mockExamId = "" } = useParams();
  const mockExam = useQuery(mockExamQuery(mockExamId));
  const resultRef = useRef<HTMLDivElement>(null);

  if (mockExam.isError) {
    // 無い模試やほかの利用者の模試は 404 になるため、ホームへ戻す
    return <Navigate to="/home" replace />;
  }
  if (!mockExam.data) {
    return null;
  }

  return (
    <PageFrame title="模試の結果">
      <div ref={resultRef} className="mx-auto w-full max-w-3xl">
        <MockExamResultView result={mockExam.data} />
      </div>
      <div className="mx-auto w-full max-w-3xl">
        <ImageExportButtons
          target={resultRef}
          fileName="teachme-mock-exam.png"
          title="模試の結果"
        />
      </div>
    </PageFrame>
  );
}
