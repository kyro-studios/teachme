import { fireEvent, render, screen } from "@testing-library/react";
import { expect, test, vi } from "vitest";
import { Button } from "./Button";

test("押すと onClick を呼び、既定では type が button になる", () => {
  const onClick = vi.fn();
  render(<Button onClick={onClick}>話す</Button>);

  fireEvent.click(screen.getByRole("button", { name: "話す" }));

  expect(onClick).toHaveBeenCalledTimes(1);
  expect(screen.getByRole("button", { name: "話す" }).getAttribute("type")).toBe("button");
});

test("無効のときは押せない", () => {
  const onClick = vi.fn();
  render(
    <Button disabled onClick={onClick}>
      判定する
    </Button>,
  );

  fireEvent.click(screen.getByRole("button", { name: "判定する" }));

  expect(onClick).not.toHaveBeenCalled();
});

test("的は 44 px 以上 (min-h-11、min-w-11) にする", () => {
  render(<Button variant="secondary">やめる</Button>);

  const className = screen.getByRole("button", { name: "やめる" }).className;
  expect(className).toContain("min-h-11");
  expect(className).toContain("min-w-11");
});
