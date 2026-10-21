import { elementToPng } from "./png";

const PROBE_FILE_NAME = "teachme.png";

// navigator.canShare が PNG の画像を受け付けるかを確かめる。対応していない端末では false
export function canShareImage(): boolean {
  if (typeof navigator.share !== "function" || typeof navigator.canShare !== "function") {
    return false;
  }
  try {
    const probe = new File([], PROBE_FILE_NAME, { type: "image/png" });
    return navigator.canShare({ files: [probe] });
  } catch {
    return false;
  }
}

// 対象の要素を PNG にして、Web Share API で端末の共有の仕組みへ渡す
export async function shareElementAsPng(
  element: HTMLElement,
  fileName: string,
  title: string,
): Promise<void> {
  if (!canShareImage()) {
    throw new Error("この端末では画像を共有できません");
  }
  const blob = await elementToPng(element);
  const file = new File([blob], fileName, { type: "image/png" });
  try {
    await navigator.share({ files: [file], title });
  } catch (error) {
    // 利用者が共有の画面を閉じたときは、失敗ではない
    if (error instanceof DOMException && error.name === "AbortError") {
      return;
    }
    throw error;
  }
}
