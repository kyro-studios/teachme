import type { ButtonHTMLAttributes } from "react";

// タップの的は 44 px 以上にする (min-h-11、min-w-11)。ホバーに頼らない
const base =
  "inline-flex min-h-11 min-w-11 items-center justify-center rounded-lg px-5 text-lg font-bold " +
  "disabled:cursor-not-allowed disabled:opacity-50 outline-offset-2 focus-visible:outline-3";

const variants = {
  primary: "bg-blue-700 text-white outline-blue-700",
  secondary: "border-2 border-slate-500 bg-white text-slate-900 outline-slate-500",
} as const;

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: keyof typeof variants;
};

export function Button({
  variant = "primary",
  className = "",
  type = "button",
  ...props
}: ButtonProps) {
  return <button type={type} className={`${base} ${variants[variant]} ${className}`} {...props} />;
}
