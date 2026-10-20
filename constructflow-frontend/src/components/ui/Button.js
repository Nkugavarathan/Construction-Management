import { Loader2 } from "lucide-react";
import { cn } from "@/lib/utils/cn";

const VARIANTS = {
  primary: "bg-primary text-white hover:bg-primary-hover",
  secondary: "bg-secondary text-white hover:bg-secondary-hover",
  outline: "border border-border bg-surface text-text hover:bg-surface-muted",
  ghost: "text-text hover:bg-neutral-soft",
  danger: "bg-danger text-white hover:opacity-90",
};

const SIZES = {
  sm: "h-8 px-3 text-sm gap-1.5",
  md: "h-10 px-4 text-sm gap-2",
  icon: "h-9 w-9 justify-center",
};

/** The one button. `loading` disables it and shows a spinner so double submits cannot happen. */
export function Button({ variant = "primary", size = "md", loading = false, disabled, className, children, type = "button", ...props }) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn(
        "inline-flex items-center rounded-md font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50 whitespace-nowrap",
        VARIANTS[variant],
        SIZES[size],
        className,
      )}
      {...props}
    >
      {loading && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
      {children}
    </button>
  );
}
