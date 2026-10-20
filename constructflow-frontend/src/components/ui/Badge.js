import { cn } from "@/lib/utils/cn";
import { STATUS_TONES } from "@/lib/constants/status";
import { humanize } from "@/lib/utils/format";

const TONES = {
  success: "bg-success-soft text-success",
  warning: "bg-warning-soft text-warning",
  danger: "bg-danger-soft text-danger",
  info: "bg-info-soft text-info",
  neutral: "bg-neutral-soft text-muted",
  brand: "bg-secondary-soft text-secondary",
};

export function Badge({ tone = "neutral", className, children }) {
  return (
    <span className={cn("inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium whitespace-nowrap", TONES[tone], className)}>
      {children}
    </span>
  );
}

/** Status pill whose colour comes from the central STATUS_TONES map. Text is always shown, never colour alone. */
export function StatusBadge({ status, className }) {
  if (!status) return null;
  return (
    <Badge tone={STATUS_TONES[status] ?? "neutral"} className={className}>
      {humanize(status)}
    </Badge>
  );
}
