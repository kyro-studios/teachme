import { expect, test } from "vitest";
import { formatJstDate } from "./date";

test("日時を日本時間の日付にして返す (UTC の前日でも、日本時間では翌日になる)", () => {
  expect(formatJstDate("2026-10-21T16:00:00Z")).toBe("2026/10/22");
});

test("日本時間の同じ日の時刻は、その日付のまま返す", () => {
  expect(formatJstDate("2026-10-22T09:30:00+09:00")).toBe("2026/10/22");
});
