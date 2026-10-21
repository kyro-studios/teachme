import { fireEvent, render, screen } from "@testing-library/react";
import { expect, test, vi } from "vitest";
import { Button } from "./Button";
import { ConfirmDialog } from "./ConfirmDialog";

function renderDialog(onConfirm = vi.fn()) {
  render(
    <ConfirmDialog
      trigger={<Button variant="secondary">教材を削除</Button>}
      title="この教材を削除しますか？"
      confirmLabel="削除する"
      cancelLabel="やめる"
      onConfirm={onConfirm}
    />,
  );
  return onConfirm;
}

test("きっかけの操作で、確認のダイアログを開く", () => {
  renderDialog();
  expect(screen.queryByRole("alertdialog")).toBeNull();

  fireEvent.click(screen.getByRole("button", { name: "教材を削除" }));

  expect(screen.getByRole("alertdialog", { name: "この教材を削除しますか？" })).toBeTruthy();
});

test("やめるを押すと、閉じて、確認を呼ばない", () => {
  const onConfirm = renderDialog();
  fireEvent.click(screen.getByRole("button", { name: "教材を削除" }));

  fireEvent.click(screen.getByRole("button", { name: "やめる" }));

  expect(onConfirm).not.toHaveBeenCalled();
  expect(screen.queryByRole("alertdialog")).toBeNull();
});

test("削除するを押すと、確認を呼んで閉じる", () => {
  const onConfirm = renderDialog();
  fireEvent.click(screen.getByRole("button", { name: "教材を削除" }));

  fireEvent.click(screen.getByRole("button", { name: "削除する" }));

  expect(onConfirm).toHaveBeenCalledTimes(1);
  expect(screen.queryByRole("alertdialog")).toBeNull();
});
