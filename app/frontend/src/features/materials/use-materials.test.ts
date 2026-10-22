import { describe, expect, test } from "vitest";
import { materialQuery, materialsQuery, ORGANIZING_POLL_INTERVAL_MS } from "./use-materials";

// 取り直しの間隔の決まり。整理中の教材があるあいだだけ、2 秒ごとに取り直す
type IntervalFn = (query: { state: { data: unknown } }) => number | false;

const listInterval = materialsQuery.refetchInterval as IntervalFn;

function listState(statuses: string[] | undefined) {
  const materials = statuses?.map((status, index) => ({
    id: `${index}`,
    number: index + 1,
    status,
  }));
  return { state: { data: statuses === undefined ? undefined : { materials } } };
}

describe("教材の一覧の取り直し", () => {
  test("整理中の教材があれば、2 秒ごとに取り直す", () => {
    expect(listInterval(listState(["organized", "organizing"]))).toBe(ORGANIZING_POLL_INTERVAL_MS);
  });

  test("整理中の教材が無ければ、取り直さない。データがまだ無いときも取り直さない", () => {
    expect(listInterval(listState(["organized", "failed"]))).toBe(false);
    expect(listInterval(listState(undefined))).toBe(false);
  });

  test("取り直しの間隔は 2 秒である", () => {
    expect(ORGANIZING_POLL_INTERVAL_MS).toBe(2000);
  });
});

describe("教材の詳細の取り直し", () => {
  test("整理中のあいだだけ、2 秒ごとに取り直す", () => {
    const detailInterval = materialQuery("material-1").refetchInterval as IntervalFn;
    expect(detailInterval({ state: { data: { status: "organizing" } } })).toBe(
      ORGANIZING_POLL_INTERVAL_MS,
    );
    expect(detailInterval({ state: { data: { status: "organized" } } })).toBe(false);
    expect(detailInterval({ state: { data: { status: "failed" } } })).toBe(false);
  });
});
