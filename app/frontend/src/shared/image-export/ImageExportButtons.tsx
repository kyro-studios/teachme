import { type RefObject, useState } from "react";
import { downloadPng } from "./png";
import { canShareImage, shareElementAsPng } from "./share";

export type ImageExportButtonsProps = {
  /** 画像にする画面の要素。押したときの表示を、ref で読む */
  target: RefObject<HTMLElement | null>;
  /** 保存するファイル名 (例: teachme-report.png) */
  fileName: string;
  /** 共有の画面に出すタイトル */
  title: string;
};

const ERROR_MESSAGE = "画像を保存できませんでした";

// shared/ui の Button は依存の向きで使えないため、同じ見た目と的の大きさをここに持つ
const BUTTON_CLASS =
  "inline-flex min-h-11 min-w-11 items-center justify-center rounded-lg border-2 border-slate-500 " +
  "bg-white px-5 text-lg font-bold text-slate-900 outline-slate-500 outline-offset-2 " +
  "focus-visible:outline-3 disabled:cursor-not-allowed disabled:opacity-50";

// 「画像を保存」と、共有に対応した端末だけの「共有する」。失敗したら、同じ文言を出す
export function ImageExportButtons({ target, fileName, title }: ImageExportButtonsProps) {
  const [canShare] = useState(canShareImage);
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);

  const run = async (action: (element: HTMLElement) => Promise<void>) => {
    const element = target.current;
    setBusy(true);
    setFailed(false);
    try {
      if (!element) {
        throw new Error("画像にする要素がありません");
      }
      await action(element);
    } catch {
      setFailed(true);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex flex-wrap items-center gap-3">
      <button
        type="button"
        className={BUTTON_CLASS}
        disabled={busy}
        onClick={() => run((element) => downloadPng(element, fileName))}
      >
        画像を保存
      </button>
      {canShare ? (
        <button
          type="button"
          className={BUTTON_CLASS}
          disabled={busy}
          onClick={() => run((element) => shareElementAsPng(element, fileName, title))}
        >
          共有する
        </button>
      ) : null}
      {failed ? (
        <p role="alert" className="text-lg font-bold text-red-700">
          {ERROR_MESSAGE}
        </p>
      ) : null}
    </div>
  );
}
