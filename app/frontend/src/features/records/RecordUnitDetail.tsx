import { useId, type ReactNode } from "react";
import { Link, useNavigate } from "react-router";
import { formatJstDate } from "../../shared/lib/date";
import { Button } from "../../shared/ui/Button";
import { ConfirmDialog } from "../../shared/ui/ConfirmDialog";
import { failureMessage } from "./record-text";
import { useDeleteRecordUnit, type RecordUnit } from "./use-records";

// 文字の大きさは持たない。見出しの中の入口は見出しの大きさに従い、一覧の項目は text-lg を足す
const LINK_CLASS =
  "inline-flex min-h-11 items-center rounded-lg px-2 font-bold text-slate-900 underline outline-offset-2 focus-visible:outline-3 focus-visible:outline-blue-700";

// 単元の画面 (仕様書 5.8、5.9)。まとめノートと模試の結果は新しい順に並べ、各項目から内容を見られる。
// 学習レポートは、見出しから開く。削除は確認を出し、実行中は押せないようにする
export function RecordUnitDetail({ unit }: { unit: RecordUnit }) {
  const navigate = useNavigate();
  const remove = useDeleteRecordUnit();

  return (
    <div className="grid gap-8">
      <Section title="まとめノート">
        <ul className="grid gap-3">
          {unit.notes.map((note) => (
            <li key={note.recordId}>
              <Link to={`/records/notes/${note.recordId}`} className={`${LINK_CLASS} text-lg`}>
                {formatJstDate(note.completedAt)}
              </Link>
            </li>
          ))}
        </ul>
      </Section>

      <Section title="模試の結果">
        <ul className="grid gap-3">
          {unit.mockExams.map((mockExam) => (
            <li
              key={mockExam.id}
              className="flex flex-wrap items-center gap-3 text-lg text-slate-900"
            >
              <Link to={`/mock-exams/${mockExam.id}`} className={`${LINK_CLASS} text-lg`}>
                {formatJstDate(mockExam.takenAt)}
              </Link>
              <span>{`得点 ${mockExam.score}、偏差値 ${mockExam.deviation.toFixed(1)}`}</span>
            </li>
          ))}
        </ul>
      </Section>

      <Section title="学習レポート" to={`/records/units/${unit.unitId}/report`} />

      {/* 削除の実行中は、開くボタンも無効にして、二重に押せないようにする */}
      <div className="grid justify-items-start gap-3">
        <ConfirmDialog
          trigger={
            <Button variant="secondary" disabled={remove.isPending}>
              この単元の学習記録を削除
            </Button>
          }
          title="この単元の学習記録を削除しますか？"
          confirmLabel="削除する"
          cancelLabel="やめる"
          onConfirm={() => remove.mutate(unit.unitId, { onSuccess: () => navigate("/records") })}
        />
        {remove.error ? (
          <p role="alert" className="text-lg font-bold text-red-700">
            {failureMessage(remove.error)}
          </p>
        ) : null}
      </div>
    </div>
  );
}

// 見出しと、その下の内容。`to` があるときは、見出しの文言そのものを、その画面への入口にする
function Section({ title, to, children }: { title: string; to?: string; children?: ReactNode }) {
  const titleId = useId();
  return (
    <section aria-labelledby={titleId} className="grid gap-3">
      <h2 id={titleId} className="text-xl font-bold text-slate-900">
        {to ? (
          <Link to={to} className={LINK_CLASS}>
            {title}
          </Link>
        ) : (
          title
        )}
      </h2>
      {children}
    </section>
  );
}
