// ペンの状態の追跡。ペン、指、マウスを同じに扱い、触れている間を「書き込み中」とする
import { frameToRegion, type Point, type Region } from "./geometry";

/** 指している範囲に含める、録音の開始より前の時間 (仕様書 5.5) */
export const POINTED_LOOKBACK_MS = 10_000;

/** 指している範囲の最大の件数 (architecture.md の `pointedRegions`) */
export const POINTED_MAX_COUNT = 10;

/** 1 回の接触。pointerdown から、pointerup か pointercancel までの触れていた範囲 */
export type Contact = {
  /** 触れ始めた時刻 (ミリ秒。`Date.now()` と同じ基準) */
  startedAt: number;
  /** 離れた時刻。まだ触れているときは、判定の時点の時刻 */
  endedAt: number;
  /** 触れていた範囲。ページの単位 (ボードの座標) */
  region: Region;
};

/** 録音の開始と終了の時刻 (ミリ秒。`Date.now()` と同じ基準) */
export type RecordingRange = { startedAt: number; endedAt: number };

/**
 * 録音の範囲の中で触れた接触の範囲を返す (ページの単位)。
 *
 * - 対象は、録音の開始の 10 秒前から終了までに、少しでも触れていた接触。
 * - 件数が上限を超えるときは、新しいものを優先する。
 * - 返す順は、触れ始めた古い順。
 */
export function selectPointedRegions(
  contacts: readonly Contact[],
  range: RecordingRange,
): Region[] {
  const from = range.startedAt - POINTED_LOOKBACK_MS;
  return contacts
    .filter((contact) => contact.endedAt >= from && contact.startedAt <= range.endedAt)
    .sort((a, b) => a.startedAt - b.startedAt)
    .slice(-POINTED_MAX_COUNT)
    .map((contact) => contact.region);
}

type Extent = { minX: number; minY: number; maxX: number; maxY: number };
type ActiveContact = { startedAt: number; extent: Extent };

function extend(extent: Extent, point: Point): void {
  extent.minX = Math.min(extent.minX, point.x);
  extent.minY = Math.min(extent.minY, point.y);
  extent.maxX = Math.max(extent.maxX, point.x);
  extent.maxY = Math.max(extent.maxY, point.y);
}

/**
 * ボードへの接触を追跡する。DOM のイベントは、呼び出し側が変換して渡す。
 * 座標は、ページの単位で渡す。画面の表示が変わっても、同じ場所を指し続けるため。
 * 時刻は `now` で数える (テストのため差し替えられる)
 */
export class PenTracker {
  private readonly active = new Map<number, ActiveContact>();
  private readonly finished: Contact[] = [];
  private releasedAt: number | null = null;

  constructor(private readonly now: () => number = Date.now) {}

  /** 触れ始めた (pointerdown) */
  press(pointerId: number, point: Point): void {
    this.active.set(pointerId, {
      startedAt: this.now(),
      extent: { minX: point.x, minY: point.y, maxX: point.x, maxY: point.y },
    });
  }

  /** 触れたまま動かした (pointermove)。触れていない指は無視する */
  move(pointerId: number, point: Point): void {
    const contact = this.active.get(pointerId);
    if (contact) {
      extend(contact.extent, point);
    }
  }

  /** 離した (pointerup)。最後にペンを離した時刻を更新する */
  release(pointerId: number): void {
    this.finish(pointerId);
  }

  /** 取り消された (pointercancel)。離したものとして扱う */
  cancel(pointerId: number): void {
    this.finish(pointerId);
  }

  /** どれか 1 つでも触れていれば、書き込み中 */
  isWriting(): boolean {
    return this.active.size > 0;
  }

  /** 最後にペンを離した時刻。まだ離していなければ null */
  lastReleasedAt(): number | null {
    return this.releasedAt;
  }

  /**
   * 録音の範囲で触れた範囲 (最大 10 件)。まだ触れている接触も含める。
   * 返す範囲は、`frame` (送った画像の枠) に対する割合
   */
  pointedRegions(range: RecordingRange, frame: Region): Region[] {
    const now = this.now();
    const live = [...this.active.values()].map((contact) => ({
      startedAt: contact.startedAt,
      endedAt: now,
      region: toRegion(contact.extent),
    }));
    return selectPointedRegions([...this.finished, ...live], range).map((region) =>
      frameToRegion(region, frame),
    );
  }

  private finish(pointerId: number): void {
    const contact = this.active.get(pointerId);
    if (!contact) {
      return;
    }
    this.active.delete(pointerId);
    const endedAt = this.now();
    this.finished.push({
      startedAt: contact.startedAt,
      endedAt,
      region: toRegion(contact.extent),
    });
    this.releasedAt = endedAt;
  }
}

function toRegion(extent: Extent): Region {
  return {
    x: extent.minX,
    y: extent.minY,
    width: extent.maxX - extent.minX,
    height: extent.maxY - extent.minY,
  };
}
