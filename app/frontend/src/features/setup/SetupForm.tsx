import { useId, useState, type FormEvent } from "react";
import type { ApiError } from "../../api/client";
import type { components } from "../../api/schema";
import { SUBJECT_LABELS } from "../../shared/lib/labels";
import { Button } from "../../shared/ui/Button";
import { CheckboxField } from "../../shared/ui/CheckboxField";
import { ChoiceGroup } from "../../shared/ui/ChoiceGroup";
import {
  LESSONS_PER_WEEK_MAX,
  LESSONS_PER_WEEK_MIN,
  PRIVACY_LABEL,
  validateSetup,
  type SettingsInput,
  type SetupFieldErrors,
  type SetupFormValues,
  type Subject,
} from "./setup-validation";

type StudentType = components["schemas"]["StudentType"];
type SavedSettings = components["schemas"]["Settings"];

export type SetupFormProps = {
  /** 未完了のときは initial (「始める」)。完了後の設定の変更は edit (「保存する」) */
  mode: "initial" | "edit";
  studentTypes: readonly StudentType[];
  /** 保存されている設定。edit のとき、教科、単元名、週の授業回数の初期値になり、生徒は変えられない */
  saved?: SavedSettings;
  pending: boolean;
  /** 保存の API が返した失敗。項目の文言 (`fields`) は、その項目の下に出す */
  serverError?: ApiError | null;
  onSubmit: (input: SettingsInput) => void;
};

const SUBJECTS: readonly Subject[] = ["math", "science"];

const LESSONS_PER_WEEK_OPTIONS = Array.from(
  { length: LESSONS_PER_WEEK_MAX - LESSONS_PER_WEEK_MIN + 1 },
  (_, index) => LESSONS_PER_WEEK_MIN + index,
);

function initialValues(saved: SavedSettings | undefined): SetupFormValues {
  return {
    studentTypeId: saved?.studentTypeId ?? null,
    subject: saved?.subject ?? null,
    unitName: saved?.unitName ?? "",
    lessonsPerWeek: saved?.lessonsPerWeek ?? null,
    privacyConfirmed: false,
  };
}

// 生徒の表示名と偏差値 (仕様書 1.3、10 節)
function StudentTypeDetails({ type }: { type: StudentType }) {
  return (
    <span className="flex flex-wrap items-baseline gap-x-5 gap-y-1">
      <span className="text-xl font-bold">{type.displayName}</span>
      <span>初期偏差値 {type.initialDeviation}</span>
      <span>目標偏差値 {type.targetDeviation}</span>
    </span>
  );
}

// 項目の下の文言。支援技術へは、入力と結びつけて伝える
function FieldError({ id, message }: { id: string; message: string | undefined }) {
  if (!message) {
    return null;
  }
  return (
    <p id={id} role="alert" className="text-lg font-bold text-red-700">
      {message}
    </p>
  );
}

// 初期セットアップの入力の画面 (仕様書 5.1)。入力の検査と、「始める」の有効条件を持つ
export function SetupForm({
  mode,
  studentTypes,
  saved,
  pending,
  serverError,
  onSubmit,
}: SetupFormProps) {
  const id = useId();
  const [values, setValues] = useState<SetupFormValues>(() => initialValues(saved));
  const [localErrors, setLocalErrors] = useState<SetupFieldErrors>({});

  const fieldErrors: Record<string, string | undefined> = {
    ...localErrors,
    ...serverError?.fields,
  };
  const generalError = serverError && !serverError.fields ? serverError.message : null;

  const studentOptions = studentTypes.map((type) => ({
    value: type.id,
    label: <StudentTypeDetails type={type} />,
  }));
  const savedStudent = studentTypes.find((type) => type.id === saved?.studentTypeId);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const result = validateSetup(values);
    if (!result.ok) {
      setLocalErrors(result.errors);
      return;
    }
    setLocalErrors({});
    onSubmit(result.input);
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="grid max-w-3xl gap-8">
      {mode === "initial" ? (
        <section className="grid gap-3">
          <h2 className="text-xl font-bold text-slate-900">担当する生徒を選んでください</h2>
          <ChoiceGroup
            label="担当する生徒"
            options={studentOptions}
            value={values.studentTypeId}
            onValueChange={(studentTypeId) =>
              setValues((current) => ({ ...current, studentTypeId }))
            }
          />
          <FieldError id={`${id}-student`} message={fieldErrors.studentTypeId} />
        </section>
      ) : (
        <section className="grid gap-3">
          <h2 className="text-xl font-bold text-slate-900">担当する生徒</h2>
          {savedStudent ? <StudentTypeDetails type={savedStudent} /> : null}
        </section>
      )}

      <section className="grid gap-3">
        <h2 className="text-xl font-bold text-slate-900">教科</h2>
        <ChoiceGroup
          label="教科"
          options={SUBJECTS.map((subject) => ({ value: subject, label: SUBJECT_LABELS[subject] }))}
          value={values.subject}
          onValueChange={(subject) => setValues((current) => ({ ...current, subject }))}
        />
        <FieldError id={`${id}-subject`} message={fieldErrors.subject} />
      </section>

      <section className="grid gap-3">
        <label htmlFor={`${id}-unit`} className="text-xl font-bold text-slate-900">
          単元名
        </label>
        <input
          id={`${id}-unit`}
          type="text"
          value={values.unitName}
          aria-invalid={fieldErrors.unitName ? true : undefined}
          aria-describedby={fieldErrors.unitName ? `${id}-unit-error` : undefined}
          onChange={(event) => {
            const unitName = event.target.value;
            setValues((current) => ({ ...current, unitName }));
          }}
          className="min-h-11 max-w-md rounded-lg border-2 border-slate-500 bg-white px-4 text-lg text-slate-900"
        />
        <FieldError id={`${id}-unit-error`} message={fieldErrors.unitName} />
      </section>

      <section className="grid gap-3">
        <label htmlFor={`${id}-lessons`} className="text-xl font-bold text-slate-900">
          週の授業回数
        </label>
        <select
          id={`${id}-lessons`}
          value={values.lessonsPerWeek ?? ""}
          aria-invalid={fieldErrors.lessonsPerWeek ? true : undefined}
          aria-describedby={fieldErrors.lessonsPerWeek ? `${id}-lessons-error` : undefined}
          onChange={(event) => {
            const lessonsPerWeek = event.target.value === "" ? null : Number(event.target.value);
            setValues((current) => ({ ...current, lessonsPerWeek }));
          }}
          className="min-h-11 w-40 rounded-lg border-2 border-slate-500 bg-white px-4 text-lg text-slate-900"
        >
          <option value="" disabled hidden />
          {LESSONS_PER_WEEK_OPTIONS.map((count) => (
            <option key={count} value={count}>
              {count} 回
            </option>
          ))}
        </select>
        <FieldError id={`${id}-lessons-error`} message={fieldErrors.lessonsPerWeek} />
      </section>

      <section className="grid gap-3">
        <CheckboxField
          label={PRIVACY_LABEL}
          checked={values.privacyConfirmed}
          onCheckedChange={(privacyConfirmed) =>
            setValues((current) => ({ ...current, privacyConfirmed }))
          }
        />
        <FieldError id={`${id}-privacy`} message={fieldErrors.privacyConfirmed} />
      </section>

      <div className="grid justify-items-start gap-3">
        <FieldError id={`${id}-general`} message={generalError ?? undefined} />
        <Button type="submit" disabled={!values.privacyConfirmed || pending}>
          {mode === "initial" ? "始める" : "保存する"}
        </Button>
      </div>
    </form>
  );
}
