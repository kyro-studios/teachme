import * as RadioGroup from "@radix-ui/react-radio-group";
import { useId, type ReactNode } from "react";

export type ChoiceOption<T extends string> = {
  value: T;
  label: ReactNode;
};

export type ChoiceGroupProps<T extends string> = {
  /** 選択肢のまとまりの名前 (支援技術へ伝える) */
  label: string;
  options: readonly ChoiceOption<T>[];
  /** 何も選んでいないときは null */
  value: T | null;
  onValueChange: (value: T) => void;
};

// 1 つだけ選ぶ選択肢。選択の丸と文言の両方を押せる。的は 44 px 以上
export function ChoiceGroup<T extends string>({
  label,
  options,
  value,
  onValueChange,
}: ChoiceGroupProps<T>) {
  const id = useId();
  return (
    <RadioGroup.Root
      aria-label={label}
      value={value ?? ""}
      onValueChange={(next) => onValueChange(next as T)}
      className="grid gap-3"
    >
      {options.map((option) => {
        const optionId = `${id}-${option.value}`;
        return (
          <div key={option.value} className="flex items-center gap-3">
            <RadioGroup.Item
              id={optionId}
              value={option.value}
              className="flex size-11 shrink-0 items-center justify-center rounded-full border-2 border-slate-500 bg-white outline-offset-2 focus-visible:outline-3 outline-blue-700 data-[state=checked]:border-blue-700"
            >
              <RadioGroup.Indicator className="block size-5 rounded-full bg-blue-700" />
            </RadioGroup.Item>
            <label htmlFor={optionId} className="min-h-11 text-lg leading-snug text-slate-900">
              {option.label}
            </label>
          </div>
        );
      })}
    </RadioGroup.Root>
  );
}
