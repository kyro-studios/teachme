import { useState, type ChangeEvent } from "react";
import { Button } from "../../shared/ui/Button";
import { CheckboxField } from "../../shared/ui/CheckboxField";
import { materialImagesError } from "./material-images";
import { failureMessage } from "./material-text";
import { useCreateMaterial } from "./use-materials";

const PHOTO_CONFIRM_LABEL = "写真に氏名、学校名、連絡先が写っていないことを確認しました";

// 教材の登録 (仕様書 5.3)。画像を 1 枚から 3 枚選び、確認のチェックを入れると「登録する」を押せる
export function MaterialRegister() {
  const [images, setImages] = useState<File[]>([]);
  const [imagesError, setImagesError] = useState<string | null>(null);
  const [confirmed, setConfirmed] = useState(false);
  // 登録が済んだら、選んだ画像を外す。入力の欄は作り直して、選択の表示も消す
  const [inputKey, setInputKey] = useState(0);
  const create = useCreateMaterial();

  function handleImagesChange(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? []);
    // 画像が変わったら、前の登録の結果は残さない。確認のチェックも、選び直すごとに入れ直す
    create.reset();
    setConfirmed(false);
    if (files.length === 0) {
      setImages([]);
      setImagesError(null);
      return;
    }
    const error = materialImagesError(files);
    setImages(error ? [] : files);
    setImagesError(error);
  }

  function handleRegister() {
    if (images.length === 0 || !confirmed) {
      return;
    }
    create.mutate(images, {
      onSuccess: () => {
        setImages([]);
        setConfirmed(false);
        setInputKey((key) => key + 1);
      },
    });
  }

  return (
    <section className="grid max-w-3xl gap-5">
      <label className="inline-flex min-h-11 w-fit cursor-pointer items-center rounded-lg border-2 border-slate-500 bg-white px-5 text-lg font-bold text-slate-900 has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-blue-700">
        教材を登録
        <input
          key={inputKey}
          type="file"
          accept="image/jpeg,image/png"
          multiple
          className="sr-only"
          onChange={handleImagesChange}
        />
      </label>

      {imagesError ? (
        <p role="alert" className="text-lg font-bold text-red-700">
          {imagesError}
        </p>
      ) : null}

      {images.length > 0 ? (
        <CheckboxField
          label={PHOTO_CONFIRM_LABEL}
          checked={confirmed}
          onCheckedChange={setConfirmed}
        />
      ) : null}

      <div>
        <Button
          disabled={images.length === 0 || !confirmed || create.isPending}
          onClick={handleRegister}
        >
          登録する
        </Button>
      </div>

      {create.error ? (
        <p role="alert" className="text-lg font-bold text-red-700">
          {failureMessage(create.error)}
        </p>
      ) : null}
    </section>
  );
}
