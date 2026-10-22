import type { MockExamResult } from "../../api/queries";
import { formatJstDate } from "../../shared/lib/date";

export type MockExamResultViewProps = {
  result: MockExamResult;
};

// 模試の結果の表示 (仕様書 5.7)。得点と偏差値、解答用紙、生徒のコメント、偏差値の推移を並べる
export function MockExamResultView({ result }: MockExamResultViewProps) {
  return (
    <article className="grid gap-6 rounded-xl border-2 border-slate-400 bg-white p-6">
      <div className="grid grid-cols-2 gap-4">
        <ScoreItem label="得点" value={String(result.score)} />
        <ScoreItem label="偏差値" value={result.deviation.toFixed(1)} />
      </div>

      <section className="grid gap-2">
        <h2 className="text-2xl font-bold text-slate-900">解答用紙</h2>
        <p className="whitespace-pre-wrap text-lg leading-relaxed text-slate-900">
          {result.answerSheet}
        </p>
      </section>

      <section className="grid gap-2">
        <h2 className="text-2xl font-bold text-slate-900">生徒のコメント</h2>
        <p className="text-lg leading-relaxed text-slate-900">{result.studentComment}</p>
      </section>

      <section className="grid gap-2">
        <h2 className="text-2xl font-bold text-slate-900">偏差値の推移</h2>
        <ul aria-label="偏差値の推移" className="grid gap-2">
          {result.history.map((point) => (
            <li
              key={point.takenAt}
              className="flex justify-between rounded-lg bg-slate-100 px-4 py-2 text-lg text-slate-900"
            >
              <span>{formatJstDate(point.takenAt)}</span>
              <span>{point.deviation.toFixed(1)}</span>
            </li>
          ))}
        </ul>
      </section>
    </article>
  );
}

// 得点と偏差値の 1 項目。項目の名前を、グループのアクセシブルな名前にする
function ScoreItem({ label, value }: { label: string; value: string }) {
  return (
    <div role="group" aria-label={label} className="grid gap-1 rounded-lg bg-slate-100 p-4">
      <p className="text-lg text-slate-900">{label}</p>
      <p className="text-4xl font-bold text-slate-900">{value}</p>
    </div>
  );
}
