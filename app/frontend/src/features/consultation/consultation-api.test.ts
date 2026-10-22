import { QueryClient } from "@tanstack/react-query";
import { describe, expect, test } from "vitest";
import { consultationEntryKey, forgetConsultationEntry } from "./consultation-api";
import { penWaitMs, remainingUntil } from "./consultation-timing";

describe("相談の開始の結果のキャッシュ", () => {
  test("中断した相談の結果は捨て、同じ教材の別の相談と、ほかの教材の相談は残す", () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(consultationEntryKey("material-1"), { consultationId: "c1" });
    queryClient.setQueryData(consultationEntryKey("material-2"), { consultationId: "c2" });

    forgetConsultationEntry(queryClient, "material-1");

    expect(queryClient.getQueryData(consultationEntryKey("material-1"))).toBeUndefined();
    expect(queryClient.getQueryData(consultationEntryKey("material-2"))).toEqual({
      consultationId: "c2",
    });
  });
});

describe("相談の時計", () => {
  test("期限までの残りは、端末の時計をサーバーの時計に合わせて数える", () => {
    // サーバーは端末より 5 秒進んでいる。期限は 10 秒後 (サーバーの時計で)
    const offset = 5_000;
    expect(remainingUntil("2026-10-22T00:00:10Z", offset, Date.parse("2026-10-22T00:00:00Z"))).toBe(
      5_000,
    );
    expect(remainingUntil("2026-10-22T00:00:10Z", 0, Date.parse("2026-10-22T00:00:20Z"))).toBe(0);
  });

  test("書き込み中は離すまで待ち、離した後は 2 秒の残りを待つ", () => {
    expect(penWaitMs({ isWriting: true, releasedAt: null }, 0)).toBeNull();
    expect(penWaitMs({ isWriting: false, releasedAt: null }, 0)).toBe(0);
    expect(penWaitMs({ isWriting: false, releasedAt: 1_000 }, 1_500)).toBe(1_500);
    expect(penWaitMs({ isWriting: false, releasedAt: 1_000 }, 4_000)).toBe(0);
  });
});
