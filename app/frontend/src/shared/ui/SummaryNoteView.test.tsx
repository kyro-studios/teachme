import { render, screen } from "@testing-library/react";
import { expect, test } from "vitest";
import { SummaryNoteView } from "./SummaryNoteView";

test("まとめ文を「今日のまとめ」の下に表示する", () => {
  render(<SummaryNoteView summaryText="頂点は平方完成で読めます。" quotes={[]} />);

  expect(screen.getByRole("heading", { name: "まとめノート" })).toBeTruthy();
  expect(screen.getByRole("heading", { name: "今日のまとめ" })).toBeTruthy();
  expect(screen.getByText("頂点は平方完成で読めます。")).toBeTruthy();
});

test("引用する板書が無いときは、「先生の図と式」を出さない", () => {
  render(<SummaryNoteView summaryText="まとめ" quotes={[]} />);

  expect(screen.queryByRole("heading", { name: "先生の図と式" })).toBeNull();
});

test("引用する板書があるときは、「先生の図と式」の下に並べる", () => {
  render(<SummaryNoteView summaryText="まとめ" quotes={["y = (x - 2)^2 - 1", "頂点 (2, -1)"]} />);

  expect(screen.getByRole("heading", { name: "先生の図と式" })).toBeTruthy();
  expect(screen.getByText("y = (x - 2)^2 - 1")).toBeTruthy();
  expect(screen.getByText("頂点 (2, -1)")).toBeTruthy();
});
