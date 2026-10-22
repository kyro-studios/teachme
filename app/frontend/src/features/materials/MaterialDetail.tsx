import { useId, type ReactNode } from "react";
import { useNavigate } from "react-router";
import { ConfirmDialog } from "../../shared/ui/ConfirmDialog";
import { Button } from "../../shared/ui/Button";
import { MaterialFigure } from "./MaterialFigure";
import { RetryMaterialButton } from "./RetryMaterialButton";
import { failureMessage } from "./material-text";
import { useDeleteMaterial, type MaterialDetail as MaterialDetailData } from "./use-materials";

// 教材の詳細 (仕様書 5.3、5.9)。整理が済んだ教材は、問題、模範解答、解説、生徒の途中の解答、指導のポイントを見出しの下に出す
export function MaterialDetail({ material }: { material: MaterialDetailData }) {
  const navigate = useNavigate();
  const remove = useDeleteMaterial();
  const { problem } = material;

  return (
    <div className="grid gap-8">
      {material.status === "organizing" ? (
        <p className="text-lg text-slate-900">整理しています</p>
      ) : null}

      {material.status === "failed" ? (
        <div className="grid justify-items-start gap-3">
          <p className="text-lg font-bold text-red-700">整理できませんでした</p>
          <RetryMaterialButton materialId={material.id} />
        </div>
      ) : null}

      {problem ? (
        <>
          <Section title="問題">
            <p className="text-lg whitespace-pre-line text-slate-900">{problem.problemText}</p>
            {problem.figures.map((figure, index) => (
              <MaterialFigure
                key={`${figure.imagePosition}-${index}`}
                materialId={material.id}
                figure={figure}
                index={index + 1}
              />
            ))}
          </Section>
          <Section title="模範解答">
            <p className="text-lg whitespace-pre-line text-slate-900">{problem.modelAnswer}</p>
          </Section>
          <Section title="解説">
            <ol className="grid list-decimal gap-2 pl-6 text-lg text-slate-900">
              {problem.explanationSteps.map((step, index) => (
                <li key={index}>{step}</li>
              ))}
            </ol>
          </Section>
          <Section title="生徒の途中の解答">
            <p className="text-lg whitespace-pre-line text-slate-900">{problem.studentWork}</p>
          </Section>
          <Section title="指導のポイント">
            <ul className="grid list-disc gap-2 pl-6 text-lg text-slate-900">
              {problem.teachingPoints.map((point, index) => (
                <li key={index}>{point}</li>
              ))}
            </ul>
          </Section>
        </>
      ) : null}

      {/* 整理中の教材は削除しない (仕様書 7.2 の状態の遷移に合わせる)。削除の実行中は、開くボタンも無効にして二重に押せないようにする */}
      {material.status !== "organizing" ? (
        <div className="grid justify-items-start gap-3">
          <ConfirmDialog
            trigger={
              <Button variant="secondary" disabled={remove.isPending}>
                教材を削除
              </Button>
            }
            title="この教材を削除しますか？"
            confirmLabel="削除する"
            cancelLabel="やめる"
            onConfirm={() =>
              remove.mutate(material.id, { onSuccess: () => navigate("/materials") })
            }
          />
          {remove.error ? (
            <p role="alert" className="text-lg font-bold text-red-700">
              {failureMessage(remove.error)}
            </p>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

// 見出しと、その下の内容。支援技術へは、見出しを領域の名前として伝える
function Section({ title, children }: { title: string; children: ReactNode }) {
  const titleId = useId();
  return (
    <section aria-labelledby={titleId} className="grid gap-3">
      <h2 id={titleId} className="text-xl font-bold text-slate-900">
        {title}
      </h2>
      {children}
    </section>
  );
}
