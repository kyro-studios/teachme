import { fireEvent, render, screen } from "@testing-library/react";
import { expect, test, vi } from "vitest";
import { CheckboxField } from "./CheckboxField";

const LABEL = "氏名、学校名、連絡先は話さず、ボードにも書かないことを確認しました";

test("文言のラベルで、チェックボックスを見つけられる", () => {
  render(<CheckboxField label={LABEL} checked={false} onCheckedChange={() => {}} />);

  expect(screen.getByRole("checkbox", { name: LABEL })).toBeTruthy();
});

test("押すと、反対の値を onCheckedChange へ渡す", () => {
  const onCheckedChange = vi.fn();
  render(<CheckboxField label={LABEL} checked={false} onCheckedChange={onCheckedChange} />);

  fireEvent.click(screen.getByRole("checkbox", { name: LABEL }));

  expect(onCheckedChange).toHaveBeenLastCalledWith(true);
});

test("チェックが入っているときに押すと、false を渡す", () => {
  const onCheckedChange = vi.fn();
  render(<CheckboxField label={LABEL} checked onCheckedChange={onCheckedChange} />);

  fireEvent.click(screen.getByRole("checkbox", { name: LABEL }));

  expect(onCheckedChange).toHaveBeenLastCalledWith(false);
});

test("無効のときは押せない", () => {
  const onCheckedChange = vi.fn();
  render(
    <CheckboxField label={LABEL} checked={false} disabled onCheckedChange={onCheckedChange} />,
  );

  fireEvent.click(screen.getByRole("checkbox", { name: LABEL }));

  expect(onCheckedChange).not.toHaveBeenCalled();
});
