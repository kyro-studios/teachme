import { useEffect, useRef, useState } from "react";
import { AVATAR_ASSETS } from "./avatar-config";
import {
  CHARACTER_LABELS,
  EXPRESSION_LABELS,
  type AvatarCharacter,
  type AvatarExpression,
  type AvatarGesture,
} from "./avatar-model";
import { canRenderLive2D, createLive2DView, type Live2DView } from "./live2d";
import { StaticAvatar } from "./StaticAvatar";

export type AvatarProps = {
  /** 話し手。生徒の種類か先輩先生。モデルと、静止画の代替の見た目を決める */
  character: AvatarCharacter;
  /** 表情 */
  expression: AvatarExpression;
  /** 身振り。値が変わったときに、1 回だけ行う */
  gesture: AvatarGesture;
  className?: string;
};

/**
 * AI 生徒と先輩先生の描画 (Live2D)。
 * Live2D が描けないとき (WebGL が無い、モデルが読めない、描画に失敗した) は、静止画の代替を出す。どちらでも進行は止めない。
 * 話し手が変わると、別のモデルを読み直す。
 */
export function Avatar({ character, expression, gesture, className = "" }: AvatarProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [view, setView] = useState<Live2DView | null>(null);
  // 一度でも描けなかったら、静止画の代替のまま進める
  const [failed, setFailed] = useState(() => !canRenderLive2D());

  useEffect(() => {
    const canvas = canvasRef.current;
    if (failed || !canvas) {
      return;
    }
    const controller = new AbortController();
    // 描画が終わる前に話し手が変わったときに捨てられるよう、結果を箱に入れて持つ
    const created: { view: Live2DView | null } = { view: null };
    createLive2DView(
      canvas,
      {
        cubismCoreUrl: AVATAR_ASSETS.cubismCoreUrl,
        modelUrl: AVATAR_ASSETS.modelUrls[character],
      },
      controller.signal,
    ).then(
      (next) => {
        if (controller.signal.aborted) {
          next.destroy();
          return;
        }
        created.view = next;
        setView(next);
      },
      () => {
        if (!controller.signal.aborted) {
          setFailed(true);
        }
      },
    );
    return () => {
      controller.abort();
      created.view?.destroy();
      setView(null);
    };
  }, [character, failed]);

  useEffect(() => {
    view?.setExpression(AVATAR_ASSETS.expressionNames[expression]);
  }, [view, expression]);

  useEffect(() => {
    view?.playMotion(AVATAR_ASSETS.motionGroups[gesture]);
  }, [view, gesture]);

  // 外側の要素が、読み上げの 1 つの画像になる。中の canvas と静止画は、読み上げの対象にしない
  return (
    <div
      role="img"
      aria-label={`${CHARACTER_LABELS[character]}、${EXPRESSION_LABELS[expression]}`}
      className={`relative aspect-square w-full ${className}`}
    >
      {view ? null : (
        <StaticAvatar character={character} expression={expression} gesture={gesture} />
      )}
      {failed ? null : (
        // キャラクターごとに canvas を作り直す。同じ canvas に別のモデルを描かないため
        <canvas
          key={character}
          ref={canvasRef}
          aria-hidden="true"
          className="absolute inset-0 h-full w-full"
        />
      )}
    </div>
  );
}
