// 日付の表示 (仕様書 5.8)。日付と週は日本時間で数える。API の日時は ISO 8601 で届くため、日本時間に直して描く

const JST_DATE_FORMAT = new Intl.DateTimeFormat("ja-JP", {
  timeZone: "Asia/Tokyo",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

/** 日時 (ISO 8601) を、日本時間の日付の形 (例: 2026/10/22) にする */
export function formatJstDate(isoDateTime: string): string {
  return JST_DATE_FORMAT.format(new Date(isoDateTime));
}
