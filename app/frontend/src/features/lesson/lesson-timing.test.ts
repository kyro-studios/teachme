import { describe, expect, test } from "vitest";
import { clockOffset, PEN_WAIT_MS, penWaitMs, remainingUntil } from "./lesson-timing";

describe("期限の時計", () => {
  test("サーバーの時計と端末の時計のずれを、残りの時間に反映する", () => {
    // 端末の時計が 5 秒遅れている (サーバーは、端末より 5 秒進んでいる)
    const deviceNow = Date.parse("2026-10-22T10:00:00Z");
    const offset = clockOffset("2026-10-22T10:00:05Z", deviceNow);

    expect(remainingUntil("2026-10-22T10:01:05Z", offset, deviceNow)).toBe(60_000);
  });

  test("ずれは受け取った時点で決め、あとから画面を読み込んでも、期限は延びない", () => {
    // 開始の結果を受け取った時点 (端末の時計は 10:00:00) で、ずれを決める
    const receivedAt = Date.parse("2026-10-22T10:00:00Z");
    const offset = clockOffset("2026-10-22T10:00:00Z", receivedAt);

    // 画面の読み込みに 30 秒かかっても、残りは 30 秒のまま (期限は 10:01:00)
    expect(remainingUntil("2026-10-22T10:01:00Z", offset, receivedAt + 30_000)).toBe(30_000);
  });

  test("期限を過ぎていれば、残りは 0", () => {
    const deviceNow = Date.parse("2026-10-22T10:00:00Z");
    const offset = clockOffset("2026-10-22T10:00:00Z", deviceNow);

    expect(remainingUntil("2026-10-22T09:59:59Z", offset, deviceNow)).toBe(0);
  });
});

describe("ペンの待ち", () => {
  const now = 10_000;

  test("書き込み中は、離すまで待つ (null)", () => {
    expect(penWaitMs({ isWriting: true, releasedAt: now - 5_000 }, now)).toBeNull();
  });

  test("一度もペンを離していなければ、すぐに出してよい", () => {
    expect(penWaitMs({ isWriting: false, releasedAt: null }, now)).toBe(0);
  });

  test("離してから 2 秒の間は、残りの時間を待つ", () => {
    expect(penWaitMs({ isWriting: false, releasedAt: now - 500 }, now)).toBe(PEN_WAIT_MS - 500);
  });

  test("離してから 2 秒過ぎていれば、すぐに出してよい", () => {
    expect(penWaitMs({ isWriting: false, releasedAt: now - PEN_WAIT_MS }, now)).toBe(0);
    expect(penWaitMs({ isWriting: false, releasedAt: now - 3_000 }, now)).toBe(0);
  });
});
