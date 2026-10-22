import type { AvatarCharacter, AvatarExpression, AvatarGesture } from "./avatar-model";

export type StaticAvatarProps = {
  character: AvatarCharacter;
  expression: AvatarExpression;
  gesture: AvatarGesture;
};

// 話し手ごとの地の色。Live2D のモデルが無いときだけ使う
const SKIN: Record<AvatarCharacter, string> = {
  intuitive: "#fdba74",
  diligent: "#86efac",
  logical: "#93c5fd",
  mentor: "#c4b5fd",
};

const INK = "#1e293b";

// 口の形。表情が変わると、顔も変わる
const MOUTH: Record<AvatarExpression, string> = {
  normal: "M75 130 Q100 146 125 130",
  troubled: "M75 142 Q100 126 125 142",
  convinced: "M66 124 Q100 166 134 124 Z",
  thinking: "M86 136 L114 136",
};

// 眉。困りは八の字、考え中は片方を上げる
const BROWS: Record<AvatarExpression, readonly string[]> = {
  normal: [],
  troubled: ["M55 72 L85 82", "M145 72 L115 82"],
  convinced: [],
  thinking: ["M118 76 Q130 66 142 76"],
};

// 身振りの静止画の代替。考えるときは、頭を少し傾ける。頷く、話すは姿勢を変えない
const TILT: Record<AvatarGesture, string> = {
  nod: "",
  think: "-rotate-6",
  talk: "",
};

// 静止画の代替。Live2D が読めないときに出す。進行を止めないため、身振りは姿勢だけで表す
export function StaticAvatar({ character, expression, gesture }: StaticAvatarProps) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 200 200"
      className={`h-full w-full transition-transform ${TILT[gesture]}`}
    >
      <circle cx="100" cy="100" r="85" fill={SKIN[character]} stroke={INK} strokeWidth="4" />
      {expression === "convinced" ? (
        <g fill="none" stroke={INK} strokeWidth="5" strokeLinecap="round">
          <path d="M57 96 Q70 84 83 96" />
          <path d="M117 96 Q130 84 143 96" />
        </g>
      ) : (
        <g fill={INK}>
          <circle cx="70" cy="95" r="8" />
          <circle cx="130" cy="95" r="8" />
        </g>
      )}
      {BROWS[expression].map((d) => (
        <path key={d} d={d} fill="none" stroke={INK} strokeWidth="5" strokeLinecap="round" />
      ))}
      <path
        data-part="mouth"
        d={MOUTH[expression]}
        fill={expression === "convinced" ? INK : "none"}
        stroke={INK}
        strokeWidth="5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {expression === "thinking" ? (
        <g fill="#ffffff" stroke={INK} strokeWidth="3">
          <circle cx="152" cy="48" r="4" />
          <circle cx="166" cy="34" r="6" />
          <circle cx="182" cy="18" r="9" />
        </g>
      ) : null}
    </svg>
  );
}
