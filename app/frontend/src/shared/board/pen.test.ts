import { expect, test } from "vitest";
import { POINTED_LOOKBACK_MS, PenTracker, selectPointedRegions, type Contact } from "./pen";

function contact(startedAt: number, endedAt: number, x: number): Contact {
  return { startedAt, endedAt, region: { x, y: 0, width: 0.1, height: 0.1 } };
}

/** 送った画像の枠。ページの単位で、接触の座標と同じ基準 */
const UNIT_FRAME = { x: 0, y: 0, width: 1, height: 1 };

/** 時刻を手で進められる時計 */
function clock(start = 1_000_000) {
  let current = start;
  return {
    now: () => current,
    advance: (ms: number) => {
      current += ms;
    },
  };
}

test("録音の 10 秒前から終わりまでに触れた接触だけを返す", () => {
  const range = { startedAt: 100_000, endedAt: 130_000 };
  const contacts = [
    contact(85_000, 89_000, 0.1), // 10 秒より前に終わった。含めない
    contact(89_000, 91_000, 0.2), // 10 秒前にかかっている。含める
    contact(120_000, 121_000, 0.3), // 録音の中。含める
    contact(131_000, 132_000, 0.4), // 録音の後。含めない
  ];

  const regions = selectPointedRegions(contacts, range);

  expect(regions.map((region) => region.x)).toEqual([0.2, 0.3]);
});

test("件数が 10 件を超えるときは、新しいものを優先し、古い順に返す", () => {
  const range = { startedAt: 100_000, endedAt: 130_000 };
  // 12 件。触れた順は、入力の順と違う (新しい接触を先に並べる)
  const contacts = Array.from({ length: 12 }, (_, index) =>
    contact(120_000 - index * 100, 120_000 - index * 100 + 50, index / 100),
  ).reverse();

  const regions = selectPointedRegions(contacts, range);

  expect(regions).toHaveLength(10);
  // 古い方の 2 件 (index 11 と 10) を除き、index 9 から 0 を古い順に返す
  expect(regions.map((region) => Math.round(region.x * 100))).toEqual([
    9, 8, 7, 6, 5, 4, 3, 2, 1, 0,
  ]);
});

test("触れた範囲が無ければ、空の配列を返す", () => {
  expect(selectPointedRegions([], { startedAt: 0, endedAt: 1000 })).toEqual([]);
});

test("書き込み中は、ペン、指、マウスのどれかが触れている間", () => {
  const time = clock();
  const tracker = new PenTracker(time.now);

  expect(tracker.isWriting()).toBe(false);

  tracker.press(1, { x: 0.1, y: 0.1 });
  tracker.press(2, { x: 0.5, y: 0.5 });
  expect(tracker.isWriting()).toBe(true);

  tracker.release(1);
  expect(tracker.isWriting()).toBe(true);

  tracker.release(2);
  expect(tracker.isWriting()).toBe(false);
});

test("最後にペンを離した時刻を持つ。取り消しも離したものとして扱う", () => {
  const time = clock(5_000);
  const tracker = new PenTracker(time.now);
  expect(tracker.lastReleasedAt()).toBeNull();

  tracker.press(1, { x: 0.1, y: 0.1 });
  time.advance(800);
  tracker.release(1);
  expect(tracker.lastReleasedAt()).toBe(5_800);

  tracker.press(2, { x: 0.2, y: 0.2 });
  time.advance(300);
  tracker.cancel(2);
  expect(tracker.lastReleasedAt()).toBe(6_100);
  expect(tracker.isWriting()).toBe(false);
});

test("触れていない指の動きと離す操作は、無視する", () => {
  const time = clock(5_000);
  const tracker = new PenTracker(time.now);

  tracker.move(9, { x: 0.9, y: 0.9 });
  tracker.release(9);

  expect(tracker.isWriting()).toBe(false);
  expect(tracker.lastReleasedAt()).toBeNull();
  expect(tracker.pointedRegions({ startedAt: 0, endedAt: 10_000 }, UNIT_FRAME)).toEqual([]);
});

test("指している範囲は、触れた間の動きを囲む矩形になる", () => {
  const time = clock(5_000);
  const tracker = new PenTracker(time.now);

  tracker.press(1, { x: 0.2, y: 0.3 });
  time.advance(100);
  tracker.move(1, { x: 0.6, y: 0.25 });
  tracker.move(1, { x: 0.4, y: 0.7 });
  time.advance(100);
  tracker.release(1);

  const regions = tracker.pointedRegions({ startedAt: 4_000, endedAt: 6_000 }, UNIT_FRAME);

  expect(regions).toHaveLength(1);
  expect(regions[0].x).toBeCloseTo(0.2);
  expect(regions[0].y).toBeCloseTo(0.25);
  expect(regions[0].width).toBeCloseTo(0.4);
  expect(regions[0].height).toBeCloseTo(0.45);
});

test("まだ触れている接触も、指している範囲に含める", () => {
  const time = clock(5_000);
  const tracker = new PenTracker(time.now);

  tracker.press(1, { x: 0.3, y: 0.3 });
  time.advance(2_000);

  const regions = tracker.pointedRegions({ startedAt: 5_000, endedAt: 6_000 }, UNIT_FRAME);

  expect(regions).toEqual([{ x: 0.3, y: 0.3, width: 0, height: 0 }]);
});

test("録音の 10 秒前より前に離した接触は、指している範囲に含めない", () => {
  const time = clock(5_000);
  const tracker = new PenTracker(time.now);

  tracker.press(1, { x: 0.3, y: 0.3 });
  tracker.release(1);
  time.advance(POINTED_LOOKBACK_MS + 1_000);

  expect(
    tracker.pointedRegions(
      { startedAt: 5_000 + POINTED_LOOKBACK_MS + 1_000, endedAt: 30_000 },
      UNIT_FRAME,
    ),
  ).toEqual([]);
});

test("指している範囲は、ページの座標で持ち、渡された枠に対する割合で返す", () => {
  const time = clock(5_000);
  const tracker = new PenTracker(time.now);
  // ページの (1200, 300) を指す。画面の表示は、この後に変わっても構わない
  tracker.press(1, { x: 1200, y: 300 });
  tracker.release(1);

  // 送った画像の枠は、ページの (1000, 200) から 1600 x 900
  const sentFrame = { x: 1000, y: 200, width: 1600, height: 900 };
  const regions = tracker.pointedRegions({ startedAt: 5_000, endedAt: 6_000 }, sentFrame);

  expect(regions).toHaveLength(1);
  expect(regions[0].x).toBeCloseTo(0.125);
  expect(regions[0].y).toBeCloseTo(0.111);
});
