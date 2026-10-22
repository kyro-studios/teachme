// Live2D の描画 (Cubism 4、pixi.js と pixi-live2d-display)。
// pixi.js と pixi-live2d-display は、描画するときに初めて読み込む。WebGL が無い端末とテストでは読まない

export type Live2DSource = {
  /** Cubism 4 のコア (live2dcubismcore.min.js) の URL */
  cubismCoreUrl: string;
  /** モデルの model3.json の URL */
  modelUrl: string;
};

/** 描画した Live2D に対して、親の部品が行う操作 */
export type Live2DView = {
  /** 表情を変える。名前は、モデルの表情の名前 */
  setExpression(name: string): void;
  /** モーションのグループを 1 回再生する。名前は、モデルのモーションのグループの名前 */
  playMotion(group: string): void;
  /** 描画を止めて、canvas の中の描画を捨てる */
  destroy(): void;
};

declare global {
  interface Window {
    Live2DCubismCore?: unknown;
  }
}

/** WebGL が使える端末だけ、Live2D の描画を試す (jsdom など WebGL の無い環境では試さない) */
export function canRenderLive2D(): boolean {
  return (
    typeof WebGL2RenderingContext !== "undefined" || typeof WebGLRenderingContext !== "undefined"
  );
}

let cubismCoreLoading: Promise<void> | null = null;

// Cubism コアは、読み込みが 1 回で済むように、結果を保持する。失敗したら次に描くときに読み直す
function loadCubismCore(url: string): Promise<void> {
  if (window.Live2DCubismCore) {
    return Promise.resolve();
  }
  cubismCoreLoading ??= new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = url;
    script.async = true;
    script.onload = () => {
      if (window.Live2DCubismCore) {
        resolve();
      } else {
        reject(new Error("Live2D Cubism Core が使えません"));
      }
    };
    script.onerror = () => reject(new Error("Live2D Cubism Core を読み込めませんでした"));
    document.head.append(script);
  }).catch((error: unknown) => {
    cubismCoreLoading = null;
    throw error;
  });
  return cubismCoreLoading;
}

/**
 * モデルを canvas に描き、表情と身振りを変えられる View を返す。
 * 読み込みや描画に失敗したら例外を投げる。signal が中断されたら、描画を始めずに例外を投げ、途中の結果を捨てる。
 */
export async function createLive2DView(
  canvas: HTMLCanvasElement,
  source: Live2DSource,
  signal: AbortSignal,
): Promise<Live2DView> {
  await loadCubismCore(source.cubismCoreUrl);
  const [pixi, live2d] = await Promise.all([
    import("pixi.js"),
    import("pixi-live2d-display/cubism4"),
  ]);
  signal.throwIfAborted();

  live2d.Live2DModel.registerTicker(pixi.Ticker);
  // 親の要素の大きさに合わせる。canvas は親の中で、絶対位置に置く (Avatar)
  const app = new pixi.Application({
    view: canvas,
    resizeTo: canvas.parentElement ?? undefined,
    backgroundAlpha: 0,
    antialias: true,
    autoDensity: true,
    resolution: window.devicePixelRatio || 1,
  });

  try {
    const model = await live2d.Live2DModel.from(source.modelUrl);
    signal.throwIfAborted();

    // 描画の大きさは、読み込んだときの大きさを基準にして、枠に収まる最大の大きさにする
    const naturalWidth = model.width;
    const naturalHeight = model.height;
    model.anchor.set(0.5, 0.5);
    app.stage.addChild(model);
    const fit = () => {
      const scale = Math.min(app.screen.width / naturalWidth, app.screen.height / naturalHeight);
      model.scale.set(scale);
      model.position.set(app.screen.width / 2, app.screen.height / 2);
    };
    fit();
    window.addEventListener("resize", fit);

    return {
      setExpression: (name) => void model.expression(name),
      playMotion: (group) => void model.motion(group),
      destroy: () => {
        window.removeEventListener("resize", fit);
        app.destroy(false, { children: true });
      },
    };
  } catch (error) {
    app.destroy(false, { children: true });
    throw error;
  }
}
