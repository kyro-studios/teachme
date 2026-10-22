import { skipToken, useQuery } from "@tanstack/react-query";
import { lazy, Suspense } from "react";
import { Navigate, useParams } from "react-router";
import { lessonEntryKey, type LessonEntry } from "../features/lesson/lesson-api";
import { useLessonFigures } from "../features/lesson/use-lesson-figures";

// ボード (tldraw) を含むため、授業の画面は開いたときに読み込む。画面の入口の読み込みを軽くするため
const LessonSession = lazy(() =>
  import("../features/lesson/LessonSession").then((module) => ({
    default: module.LessonSession,
  })),
);

// 授業の画面 (仕様書 5.5)。開始の結果は、授業を始めたときに受け取ったものを使う。
// リロードすると、サーバーでは中断された後のため、授業の開始の画面へ戻す
export function LessonPage() {
  const { lessonId = "" } = useParams();
  const entry = useQuery<LessonEntry>({
    queryKey: lessonEntryKey(lessonId),
    queryFn: skipToken,
  });

  if (!entry.data) {
    return <Navigate to="/lessons/new" replace />;
  }
  return <LessonWithFigures entry={entry.data} />;
}

// 図表の画像が揃ってから、授業の画面を作る (ボードは最初の内容で一度だけ作るため)
function LessonWithFigures({ entry }: { entry: LessonEntry }) {
  const figures = useLessonFigures(entry);
  if (!figures.data) {
    return null;
  }
  return (
    <Suspense fallback={null}>
      <LessonSession entry={entry} figures={figures.data} />
    </Suspense>
  );
}
