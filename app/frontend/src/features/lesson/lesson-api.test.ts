import { QueryClient } from "@tanstack/react-query";
import { describe, expect, test } from "vitest";
import { forgetLessonEntry, lessonEntryKey } from "./lesson-api";

describe("授業の開始の結果のキャッシュ", () => {
  test("中断した授業の結果は捨て、戻る、進むで来ても残らない", () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(lessonEntryKey("lesson-1"), { lessonId: "lesson-1" });
    queryClient.setQueryData(lessonEntryKey("lesson-2"), { lessonId: "lesson-2" });

    forgetLessonEntry(queryClient, "lesson-1");

    expect(queryClient.getQueryData(lessonEntryKey("lesson-1"))).toBeUndefined();
    // ほかの授業の結果は残す
    expect(queryClient.getQueryData(lessonEntryKey("lesson-2"))).toEqual({ lessonId: "lesson-2" });
  });
});
