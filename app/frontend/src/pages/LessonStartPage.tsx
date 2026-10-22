import { LessonStartForm } from "../features/lesson/LessonStartForm";
import { PageFrame } from "../shared/ui/PageFrame";

// 授業の開始 (仕様書 5.5)。整理済みの教材を 1 つ選び、授業を始める
export function LessonStartPage() {
  return (
    <PageFrame title="授業の開始">
      <LessonStartForm />
    </PageFrame>
  );
}
