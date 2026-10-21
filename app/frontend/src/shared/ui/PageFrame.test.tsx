import { render, screen } from "@testing-library/react";
import { expect, test } from "vitest";
import { PageFrame } from "./PageFrame";

test("画面の見出し (h1) に題名を出し、子の要素を並べる", () => {
  render(
    <PageFrame title="先生準備室">
      <p>教材はまだありません</p>
    </PageFrame>,
  );

  expect(screen.getByRole("heading", { level: 1, name: "先生準備室" })).toBeTruthy();
  expect(screen.getByText("教材はまだありません")).toBeTruthy();
});
