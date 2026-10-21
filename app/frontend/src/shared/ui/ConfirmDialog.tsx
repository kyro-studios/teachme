import * as AlertDialog from "@radix-ui/react-alert-dialog";
import type { ReactNode } from "react";
import { Button } from "./Button";

export type ConfirmDialogProps = {
  /** 開くきっかけの操作 (ボタンなど)。Radix の asChild で、この要素が開く操作になる */
  trigger: ReactNode;
  title: string;
  description?: string;
  confirmLabel: string;
  cancelLabel: string;
  onConfirm: () => void;
};

// 取り消せない操作の前に出す確認。Radix の AlertDialog を使い、外側を押しても閉じない
export function ConfirmDialog({
  trigger,
  title,
  description,
  confirmLabel,
  cancelLabel,
  onConfirm,
}: ConfirmDialogProps) {
  return (
    <AlertDialog.Root>
      <AlertDialog.Trigger asChild>{trigger}</AlertDialog.Trigger>
      <AlertDialog.Portal>
        <AlertDialog.Overlay className="fixed inset-0 bg-slate-900/50" />
        <AlertDialog.Content className="fixed top-1/2 left-1/2 flex w-[min(90vw,32rem)] -translate-x-1/2 -translate-y-1/2 flex-col gap-4 rounded-xl bg-white p-6 shadow-xl">
          <AlertDialog.Title className="text-2xl font-bold text-slate-900">
            {title}
          </AlertDialog.Title>
          {description ? (
            <AlertDialog.Description className="text-lg text-slate-700">
              {description}
            </AlertDialog.Description>
          ) : null}
          <div className="mt-2 flex justify-end gap-3">
            <AlertDialog.Cancel asChild>
              <Button variant="secondary">{cancelLabel}</Button>
            </AlertDialog.Cancel>
            <AlertDialog.Action asChild>
              <Button onClick={onConfirm}>{confirmLabel}</Button>
            </AlertDialog.Action>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  );
}
