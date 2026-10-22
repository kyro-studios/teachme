import { useState, type ChangeEvent } from "react";
import { ApiError } from "../../api/client";
import { HOMEWORK_VERDICT_LABELS } from "../../shared/lib/labels";
import { Button } from "../../shared/ui/Button";
import { CheckboxField } from "../../shared/ui/CheckboxField";
import { useCheckHomework } from "./use-check-homework";
import { homeworkPhotoError } from "./homework-photo";

const PHOTO_CONFIRM_LABEL = "写真に氏名、学校名、連絡先が写っていないことを確認しました";

// 宿題の丸つけ (仕様書 5.2)。写真を 1 枚選び、確認のチェックを入れると「判定する」を押せる
export function HomeworkCheck() {
  const [photo, setPhoto] = useState<File | null>(null);
  const [photoError, setPhotoError] = useState<string | null>(null);
  const [confirmed, setConfirmed] = useState(false);
  const check = useCheckHomework();

  function handlePhotoChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0] ?? null;
    // 写真が変わったら、前の判定の結果は残さない。確認のチェックも、写真ごとに入れ直す
    check.reset();
    setConfirmed(false);
    if (!file) {
      setPhoto(null);
      setPhotoError(null);
      return;
    }
    const error = homeworkPhotoError(file);
    setPhoto(error ? null : file);
    setPhotoError(error);
  }

  function handleJudge() {
    if (photo && confirmed) {
      check.mutate(photo);
    }
  }

  const failure = check.error
    ? check.error instanceof ApiError
      ? check.error.message
      : "サーバーとの通信に失敗しました"
    : null;

  return (
    <div className="grid max-w-3xl gap-5">
      <label className="inline-flex min-h-11 w-fit cursor-pointer items-center rounded-lg border-2 border-slate-500 bg-white px-5 text-lg font-bold text-slate-900 has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-blue-700">
        宿題の写真を登録
        <input
          type="file"
          accept="image/jpeg,image/png"
          className="sr-only"
          onChange={handlePhotoChange}
        />
      </label>

      {photoError ? (
        <p role="alert" className="text-lg font-bold text-red-700">
          {photoError}
        </p>
      ) : null}

      {photo ? (
        <CheckboxField
          label={PHOTO_CONFIRM_LABEL}
          checked={confirmed}
          onCheckedChange={setConfirmed}
        />
      ) : null}

      <div>
        <Button disabled={!photo || !confirmed || check.isPending} onClick={handleJudge}>
          判定する
        </Button>
      </div>

      {failure ? (
        <p role="alert" className="text-lg font-bold text-red-700">
          {failure}
        </p>
      ) : null}

      {check.data ? (
        <section className="grid gap-2">
          <h2 className="text-xl font-bold text-slate-900">判定</h2>
          <p className="text-3xl font-bold text-slate-900">
            {HOMEWORK_VERDICT_LABELS[check.data.verdict]}
          </p>
          {check.data.comment ? (
            <p className="text-lg text-slate-900">{check.data.comment}</p>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}
