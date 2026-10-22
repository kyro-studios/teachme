// 授業の時計 (期限) と、ペンの待ち (書き終えた後の 2 秒)。端末の時計は、サーバーの `serverNow` とのずれを直して数える

/** 書き終えた後、応答を出すまでの待ち (ミリ秒。仕様書 5.5) */
export const PEN_WAIT_MS = 2_000;

/** 書き込み中の確認の間隔 (ミリ秒)。離すまで、この間隔で見る */
export const PEN_POLL_MS = 100;

/** サーバーの時計から、端末の時計を引いたずれ (ミリ秒)。`deviceNow` は `Date.now()` と同じ基準 */
export function clockOffset(serverNow: string, deviceNow: number): number {
  return Date.parse(serverNow) - deviceNow;
}

/** 期限までの残り (ミリ秒)。過ぎていれば 0 */
export function remainingUntil(deadlineAt: string, offset: number, deviceNow: number): number {
  return Math.max(0, Date.parse(deadlineAt) - (deviceNow + offset));
}

/**
 * 応答を出してよいまでの待ち (ミリ秒)。0 なら今出してよい。
 * 書き込み中は null を返す (離すまで待つ)。離した直後は、離してから 2 秒の残りを返す
 */
export function penWaitMs(
  pen: { isWriting: boolean; releasedAt: number | null },
  deviceNow: number,
): number | null {
  if (pen.isWriting) {
    return null;
  }
  if (pen.releasedAt === null) {
    return 0;
  }
  return Math.max(0, pen.releasedAt + PEN_WAIT_MS - deviceNow);
}
