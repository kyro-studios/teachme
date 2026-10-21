// 画面に出す文言の対応。API の値 (英字) を、仕様書 10 節の表示名にする

/** 教科の表示名 (仕様書 1.2、5.1)。キーは API の Subject と同じ値 */
export const SUBJECT_LABELS: Readonly<Record<"math" | "science", string>> = {
  math: "数学",
  science: "理科",
};
