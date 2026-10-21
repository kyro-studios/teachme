import * as Checkbox from "@radix-ui/react-checkbox";
import { useId, type ReactNode } from "react";

export type CheckboxFieldProps = {
  label: ReactNode;
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  disabled?: boolean;
};

// 文言の横のラベルを押しても切り替わる。的は 44 px 以上
export function CheckboxField({ label, checked, onCheckedChange, disabled }: CheckboxFieldProps) {
  const id = useId();
  return (
    <div className="flex items-center gap-3">
      <Checkbox.Root
        id={id}
        checked={checked}
        disabled={disabled}
        onCheckedChange={(value) => onCheckedChange(value === true)}
        className="flex size-11 shrink-0 items-center justify-center rounded-md border-2 border-slate-500 bg-white text-xl font-bold text-blue-700 outline-offset-2 focus-visible:outline-3 outline-blue-700 disabled:opacity-50"
      >
        <Checkbox.Indicator aria-hidden>✓</Checkbox.Indicator>
      </Checkbox.Root>
      <label htmlFor={id} className="min-h-11 text-lg leading-snug text-slate-900">
        {label}
      </label>
    </div>
  );
}
