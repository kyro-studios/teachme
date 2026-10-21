// ボードの初期配置の計算。図表を問題文の下に、縦に並べる

/** 大きさ (ピクセル、またはボードの単位) */
export type Size = { width: number; height: number };

/** ボードの上の配置。左上の位置と、大きさ */
export type Placement = Size & { x: number; y: number };

/** 幅が `maxWidth` を超えるときだけ、縦横の比を保って縮小する。拡大はしない */
export function fitWidth(size: Size, maxWidth: number): Size {
  const scale = size.width > maxWidth ? maxWidth / size.width : 1;
  return { width: size.width * scale, height: size.height * scale };
}

/**
 * 図表を、上から順に縦に並べる。各図表は、幅を `maxWidth` に収めた大きさにする。
 * 返す配列の順は、入力の順と同じ
 */
export function stackFigures(
  sizes: readonly Size[],
  options: { x: number; top: number; maxWidth: number; gap: number },
): Placement[] {
  let y = options.top;
  return sizes.map((size) => {
    const fitted = fitWidth(size, options.maxWidth);
    const placement = { x: options.x, y, ...fitted };
    y += fitted.height + options.gap;
    return placement;
  });
}
