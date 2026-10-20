import { AlertTriangle, Inbox, Lock, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { ApiError, errorMessage } from "@/lib/api/client";
import { cn } from "@/lib/utils/cn";

export function EmptyState({ icon: Icon = Inbox, title, description, action, className }) {
  return (
    <div className={cn("flex flex-col items-center justify-center px-6 py-12 text-center", className)}>
      <div className="mb-3 rounded-full bg-neutral-soft p-3 text-muted">
        <Icon className="h-6 w-6" aria-hidden="true" />
      </div>
      <h3 className="text-sm font-semibold text-text">{title}</h3>
      {description && <p className="mt-1 max-w-sm text-sm text-muted">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

/** Error view for failed queries. A 403 gets its own "no access" message instead of a generic failure. */
export function ErrorState({ error, onRetry, className }) {
  if (error instanceof ApiError && error.isForbidden) {
    return (
      <EmptyState
        icon={Lock}
        title="You don't have access to this"
        description="Your role or project membership doesn't allow this view. Ask an administrator if you need it."
        className={className}
      />
    );
  }
  if (error instanceof ApiError && error.isNotFound) {
    return <EmptyState icon={Inbox} title="Not found" description="This record doesn't exist or isn't available to you." className={className} />;
  }
  return (
    <div role="alert" className={cn("flex flex-col items-center justify-center px-6 py-12 text-center", className)}>
      <div className="mb-3 rounded-full bg-danger-soft p-3 text-danger">
        <AlertTriangle className="h-6 w-6" aria-hidden="true" />
      </div>
      <h3 className="text-sm font-semibold">Couldn&apos;t load this</h3>
      <p className="mt-1 max-w-sm text-sm text-muted">{errorMessage(error)}</p>
      {onRetry && (
        <Button variant="outline" size="sm" className="mt-4" onClick={onRetry}>
          <RefreshCw className="h-4 w-4" aria-hidden="true" /> Try again
        </Button>
      )}
    </div>
  );
}

export function Skeleton({ className }) {
  return <div className={cn("animate-pulse rounded-md bg-neutral-soft", className)} aria-hidden="true" />;
}

export function LoadingSkeleton({ rows = 4, className }) {
  return (
    <div className={cn("space-y-3 p-4", className)} role="status" aria-label="Loading">
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} className="h-10 w-full" />
      ))}
    </div>
  );
}

export function Card({ title, description, actions, children, className, bodyClassName }) {
  return (
    <section className={cn("rounded-lg border border-border bg-surface shadow-sm", className)}>
      {(title || actions) && (
        <header className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-3">
          <div>
            {title && <h2 className="text-sm font-semibold">{title}</h2>}
            {description && <p className="text-xs text-muted">{description}</p>}
          </div>
          {actions && <div className="flex items-center gap-2">{actions}</div>}
        </header>
      )}
      <div className={bodyClassName}>{children}</div>
    </section>
  );
}

export function StatCard({ label, value, hint, icon: Icon, tone = "primary", loading }) {
  const toneClass = {
    primary: "bg-info-soft text-primary",
    success: "bg-success-soft text-success",
    warning: "bg-warning-soft text-warning",
    danger: "bg-danger-soft text-danger",
    brand: "bg-secondary-soft text-secondary",
  }[tone];
  return (
    <div className="rounded-lg border border-border bg-surface p-4 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase tracking-wide text-muted">{label}</p>
          {loading ? <Skeleton className="mt-2 h-7 w-20" /> : <p className="mt-1 truncate text-2xl font-semibold">{value}</p>}
          {hint && <p className="mt-1 text-xs text-muted">{hint}</p>}
        </div>
        {Icon && (
          <div className={cn("rounded-md p-2", toneClass)}>
            <Icon className="h-5 w-5" aria-hidden="true" />
          </div>
        )}
      </div>
    </div>
  );
}

export function ProgressBar({ value = 0, tone = "secondary", label }) {
  const clamped = Math.max(0, Math.min(100, Number(value) || 0));
  const color = tone === "danger" ? "bg-danger" : tone === "warning" ? "bg-warning" : "bg-secondary";
  return (
    <div className="flex items-center gap-2">
      <div
        className="h-2 flex-1 overflow-hidden rounded-full bg-neutral-soft"
        role="progressbar"
        aria-valuenow={clamped}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={label ?? "Progress"}
      >
        <div className={cn("h-full rounded-full", color)} style={{ width: `${clamped}%` }} />
      </div>
      <span className="w-10 text-right text-xs tabular-nums text-muted">{Math.round(clamped)}%</span>
    </div>
  );
}
