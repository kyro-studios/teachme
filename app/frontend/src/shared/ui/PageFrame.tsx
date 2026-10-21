import type { ReactNode } from "react";

// 画面の枠。タブレットの横向きを基準に、左右に余白を取り、幅を抑える
export function PageFrame({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-5xl flex-col gap-6 px-6 py-8 landscape:py-5">
      <h1 className="text-3xl font-bold text-slate-900">{title}</h1>
      {children}
    </main>
  );
}
