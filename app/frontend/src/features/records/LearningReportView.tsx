import type { LearningReport } from "./use-records";

export type LearningReportViewProps = {
  report: LearningReport;
};

// 学習レポートの内容 (仕様書 5.8)。見出しの下に、単元名、授業の回数、教えた問題の数、最新の模試を並べる。理解度は出さない。
// 授業が 1 回も無いときは、まだ授業がありません だけを出す
export function LearningReportView({ report }: LearningReportViewProps) {
  const { latestMockExam } = report;
  return (
    <article className="grid gap-5 rounded-xl border-2 border-slate-400 bg-white p-6">
      <h2 className="text-2xl font-bold text-slate-900">学習レポート</h2>
      <p className="text-lg text-slate-900">{`単元名: ${report.unitName}`}</p>
      {report.lessonCount === 0 ? (
        <p className="text-lg text-slate-900">まだ授業がありません</p>
      ) : (
        <>
          <p className="text-lg text-slate-900">{`授業の回数: ${report.lessonCount} 回`}</p>
          <p className="text-lg text-slate-900">{`教えた問題の数: ${report.taughtProblemCount} 問`}</p>
          {latestMockExam ? (
            <p className="text-lg text-slate-900">
              {`最新の模試: 得点 ${latestMockExam.score}、偏差値 ${latestMockExam.deviation.toFixed(1)}`}
            </p>
          ) : (
            <p className="text-lg text-slate-900">まだ模試がありません</p>
          )}
        </>
      )}
    </article>
  );
}
