// 画面に出す文言の対応。API の値 (英字) を、仕様書 10 節の表示名にする

/** 教科の表示名 (仕様書 1.2、5.1)。キーは API の Subject と同じ値 */
export const SUBJECT_LABELS: Readonly<Record<"math" | "science", string>> = {
  math: "数学",
  science: "理科",
};

/** 今日のモードの表示名 (openapi.yaml の TodayMode)。キーは API の TodayMode と同じ値 */
export const TODAY_MODE_LABELS: Readonly<
  Record<"normal" | "self_study" | "homework_check", string>
> = {
  normal: "通常",
  self_study: "自習指示",
  homework_check: "宿題の丸つけ",
};

/** 宿題の判定の文言 (仕様書 5.2、10 節)。キーは API の HomeworkResult.verdict と同じ値 */
export const HOMEWORK_VERDICT_LABELS: Readonly<
  Record<"correct" | "incorrect" | "undetermined", string>
> = {
  correct: "正解",
  incorrect: "不正解",
  undetermined: "判定できませんでした。撮り直してください",
};
