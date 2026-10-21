import {
  createBrowserRouter,
  redirect,
  type LoaderFunctionArgs,
  type RouteObject,
} from "react-router";
import { configQuery, settingsQuery } from "../api/queries";
import { ConsultationPage } from "../pages/ConsultationPage";
import { HomePage } from "../pages/HomePage";
import { LessonPage } from "../pages/LessonPage";
import { LessonStartPage } from "../pages/LessonStartPage";
import { MaterialDetailPage } from "../pages/MaterialDetailPage";
import { MaterialsPage } from "../pages/MaterialsPage";
import { MockExamPage } from "../pages/MockExamPage";
import { RecordUnitPage } from "../pages/RecordUnitPage";
import { RecordsPage } from "../pages/RecordsPage";
import { ReportPage } from "../pages/ReportPage";
import { SetupPage } from "../pages/SetupPage";
import { SummaryNotePage } from "../pages/SummaryNotePage";
import { gateRedirect } from "./gate";
import { queryClient } from "./query-client";

// 実行時の設定と利用者の設定を読み、入口の判定をする。すべての画面の前に通る
async function gateLoader({ request }: LoaderFunctionArgs) {
  const [, settings] = await Promise.all([
    queryClient.ensureQueryData(configQuery),
    queryClient.ensureQueryData(settingsQuery),
  ]);
  const target = gateRedirect(new URL(request.url).pathname, settings.completed);
  return target ? redirect(target) : null;
}

// 画面の URL。1 つの URL に 1 つの画面を置く。ID は UUID (openapi.yaml の path パラメーター)
export const routes: RouteObject[] = [
  {
    loader: gateLoader,
    children: [
      // 「/」は gateLoader が、設定の有無に応じて /setup か /home へ寄せる
      { index: true },
      { path: "setup", element: <SetupPage /> },
      { path: "home", element: <HomePage /> },
      { path: "materials", element: <MaterialsPage /> },
      { path: "materials/:materialId", element: <MaterialDetailPage /> },
      { path: "materials/:materialId/consultation", element: <ConsultationPage /> },
      { path: "lessons/new", element: <LessonStartPage /> },
      { path: "lessons/:lessonId", element: <LessonPage /> },
      { path: "mock-exams/:mockExamId", element: <MockExamPage /> },
      { path: "records", element: <RecordsPage /> },
      { path: "records/units/:unitId", element: <RecordUnitPage /> },
      { path: "records/units/:unitId/report", element: <ReportPage /> },
      { path: "records/notes/:recordId", element: <SummaryNotePage /> },
      { path: "*", loader: () => redirect("/") },
    ],
  },
];

export const router = createBrowserRouter(routes);
