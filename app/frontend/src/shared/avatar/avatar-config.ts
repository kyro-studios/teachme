// Live2D のモデルの設定。実際のモデルは人が用意し、下のパスに置く (配置の場所は VITE_AVATAR_BASE_PATH で差し替えられる)
// モデルが無い、または読めないときは、静止画の代替を出すため、ここのパスが無効でも画面は崩れない
import type { AvatarCharacter, AvatarExpression, AvatarGesture } from "./avatar-model";

const BASE_PATH: string = import.meta.env.VITE_AVATAR_BASE_PATH || "/live2d";

export const AVATAR_ASSETS = {
  // Cubism 4 のコア (live2dcubismcore.min.js)。Live2D を描く前に、1 回だけ読み込む
  cubismCoreUrl: `${BASE_PATH}/live2dcubismcore.min.js`,

  // 話し手ごとのモデル (model3.json)
  modelUrls: {
    intuitive: `${BASE_PATH}/intuitive/model3.json`,
    diligent: `${BASE_PATH}/diligent/model3.json`,
    logical: `${BASE_PATH}/logical/model3.json`,
    mentor: `${BASE_PATH}/mentor/model3.json`,
  } satisfies Record<AvatarCharacter, string>,

  // 表情を、モデルの表情の名前にする (model3.json の定義の Name)
  expressionNames: {
    normal: "normal",
    troubled: "troubled",
    convinced: "convinced",
    thinking: "thinking",
  } satisfies Record<AvatarExpression, string>,

  // 身振りを、モデルのモーションのグループの名前にする (model3.json の Motions のキー)
  motionGroups: {
    nod: "nod",
    think: "think",
    talk: "talk",
  } satisfies Record<AvatarGesture, string>,
};
