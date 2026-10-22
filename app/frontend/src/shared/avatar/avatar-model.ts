// AI 生徒と先輩先生の見た目の語彙。表情と身振りは仕様書 5.3 と 5.5、表示名は仕様書 1.3 と 10 節に従う

/**
 * 表情。「考え中」は相槌のときだけ使う (API の Expression には含めない。openapi.yaml 参照)。
 * 仕様書 10 節の「通常」「困り」「納得」「考え中」に対応する
 */
export const AVATAR_EXPRESSIONS = ["normal", "troubled", "convinced", "thinking"] as const;
export type AvatarExpression = (typeof AVATAR_EXPRESSIONS)[number];

/** 身振り。「頷く」「考える」「話している」に対応する */
export const AVATAR_GESTURES = ["nod", "think", "talk"] as const;
export type AvatarGesture = (typeof AVATAR_GESTURES)[number];

/**
 * 話し手。生徒の種類 (API の StudentTypeId と同じ値) と、先輩先生。
 * 話し手ごとに、モデルと静止画の代替の見た目が変わる
 */
export const AVATAR_CHARACTERS = ["intuitive", "diligent", "logical", "mentor"] as const;
export type AvatarCharacter = (typeof AVATAR_CHARACTERS)[number];

/** 表情の表示名 (仕様書 10 節) */
export const EXPRESSION_LABELS: Readonly<Record<AvatarExpression, string>> = {
  normal: "通常",
  troubled: "困り",
  convinced: "納得",
  thinking: "考え中",
};

/** 話し手の表示名 (仕様書 1.3 の画面の表示名)。読み上げの名前に使う */
export const CHARACTER_LABELS: Readonly<Record<AvatarCharacter, string>> = {
  intuitive: "直感・感覚派",
  diligent: "真面目な暗記派",
  logical: "論理・ツッコミ派",
  mentor: "先輩先生",
};
