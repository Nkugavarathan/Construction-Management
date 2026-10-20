"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, CalendarClock, ClipboardCheck, FolderKanban, PackageX, Wallet } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, EmptyState, ErrorState, LoadingSkeleton, ProgressBar, StatCard } from "@/components/feedback/States";
import { StatusBadge } from "@/components/ui/Badge";
import { useAuth } from "@/features/auth/AuthProvider";
import { api } from "@/lib/api/client";
import { formatDate, formatMoney, formatPercent, humanize, todayIso } from "@/lib/utils/format";
import { SpendByCategoryChart } from "./SpendByCategoryChart";

const RISK_LEVELS = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];

export function DashboardView() {
  const { me } = useAuth();
  const { data, error, isPending, refetch } = useQuery({
    queryKey: ["dashboard"],
    queryFn: () => api.get("/dashboard/summary"),
  });

  const header = (
    <PageHeader
      title={`Good to see you, ${me?.user?.fullName?.split(" ")[0] ?? ""}`}
      description={`${me?.activeOrganization?.name} · live figures for the projects you can access`}
    />
  );

  if (error) return <>{header}<Card><ErrorState error={error} onRetry={refetch} /></Card></>;
  if (isPending) return <>{header}<LoadingSkeleton rows={8} /></>;

  const currency = data.currencyCode;
  const active = data.projectsByStatus.ACTIVE ?? 0;
  const today = todayIso();

  return (
    <>
      {header}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Active projects" value={active} hint={`${data.projectsByStatus.PLANNING ?? 0} in planning · ${data.projectsByStatus.ON_HOLD ?? 0} on hold`} icon={FolderKanban} />
        <StatCard label="Overdue tasks" value={data.overdueTasks} hint={`${data.myOpenTasks} open task(s) assigned to you`} icon={AlertTriangle} tone={data.overdueTasks > 0 ? "danger" : "success"} />
        {data.finance ? (
          <StatCard
            label="Budget used (active)"
            value={formatPercent(data.finance.utilizationPercent)}
            hint={`${formatMoney(data.finance.approvedSpend, currency, { compact: true })} of ${formatMoney(data.finance.activeBudget, currency, { compact: true })}`}
            icon={Wallet}
            tone="brand"
          />
        ) : (
          <StatCard label="Open safety incidents" value={data.openIncidents} icon={AlertTriangle} tone={data.openIncidents ? "warning" : "success"} />
        )}
        {data.lowStockMaterials !== null && data.lowStockMaterials !== undefined ? (
          <StatCard label="Low-stock materials" value={data.lowStockMaterials} hint="At or below reorder level" icon={PackageX} tone={data.lowStockMaterials ? "warning" : "success"} />
        ) : (
          <StatCard label="Awaiting your approval" value={data.pendingApprovals} hint="Submitted expenses" icon={ClipboardCheck} tone={data.pendingApprovals ? "warning" : "success"} />
        )}
      </div>

      {data.pendingApprovals > 0 && (
        <Link href="/finance/expenses?status=SUBMITTED" className="mt-4 flex items-center gap-2 rounded-lg border border-warning/30 bg-warning-soft px-4 py-3 text-sm text-warning hover:underline">
          <ClipboardCheck className="h-4 w-4" aria-hidden="true" />
          {data.pendingApprovals} expense(s) are waiting for your decision.
        </Link>
      )}

      <div className="mt-6 grid gap-6 lg:grid-cols-3">
        <Card title="Active projects" description="Weighted task progress and approved spend" className="lg:col-span-2">
          {data.activeProjects.length === 0 ? (
            <EmptyState title="No active projects" description="Projects you can access will appear here once they start." />
          ) : (
            <ul className="divide-y divide-border">
              {data.activeProjects.map((p) => (
                <li key={p.id} className="grid gap-2 px-4 py-3 sm:grid-cols-[1fr_180px] sm:items-center">
                  <div className="min-w-0">
                    <Link href={`/projects/${p.id}`} className="font-medium hover:underline">{p.name}</Link>
                    <p className="text-xs text-muted">
                      {p.code} · due {formatDate(p.plannedEndDate)}
                      {p.budgetUtilizationPercent !== null && ` · ${formatPercent(p.budgetUtilizationPercent)} of budget spent`}
                    </p>
                  </div>
                  <ProgressBar value={p.progressPercent} label={`${p.name} progress`} />
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card title="Upcoming milestones" description="Due in the next 14 days, including overdue">
          {data.upcomingMilestones.length === 0 ? (
            <EmptyState icon={CalendarClock} title="Nothing due soon" />
          ) : (
            <ul className="divide-y divide-border">
              {data.upcomingMilestones.map((m) => (
                <li key={m.id} className="px-4 py-3">
                  <Link href={`/projects/${m.projectId}/milestones`} className="text-sm font-medium hover:underline">{m.name}</Link>
                  <p className="text-xs text-muted">
                    {m.projectCode} · <span className={m.dueDate < today ? "font-medium text-danger" : ""}>{m.dueDate < today ? "overdue since " : "due "}{formatDate(m.dueDate)}</span>
                  </p>
                </li>
              ))}
            </ul>
          )}
        </Card>

        {data.finance && (
          <Card title="Approved spend by category" description="All projects whose finances you can see" className="lg:col-span-2" bodyClassName="p-4">
            {data.finance.spendByCategory.length === 0 ? (
              <EmptyState title="No approved spend yet" />
            ) : (
              <SpendByCategoryChart rows={data.finance.spendByCategory} currency={currency} />
            )}
          </Card>
        )}

        <Card title="Open risks by level" description="Score = likelihood × impact" bodyClassName="p-4">
          <RiskLevels distribution={data.openRisksByLevel} />
        </Card>

        {data.purchaseOrdersByStatus && (
          <Card title="Purchase orders" bodyClassName="p-4">
            {Object.keys(data.purchaseOrdersByStatus).length === 0 ? (
              <EmptyState title="No purchase orders yet" />
            ) : (
              <ul className="space-y-2">
                {Object.entries(data.purchaseOrdersByStatus).map(([status, count]) => (
                  <li key={status} className="flex items-center justify-between text-sm">
                    <StatusBadge status={status} />
                    <span className="font-medium tabular-nums">{count}</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        )}
      </div>
    </>
  );
}

/** Ordinal severity shown as labelled single-hue bars: the label carries the meaning, not the colour. */
function RiskLevels({ distribution }) {
  const max = Math.max(1, ...RISK_LEVELS.map((l) => distribution?.[l] ?? 0));
  const total = RISK_LEVELS.reduce((sum, l) => sum + (distribution?.[l] ?? 0), 0);
  if (total === 0) return <EmptyState title="No open risks" />;
  return (
    <ul className="space-y-3">
      {RISK_LEVELS.map((level) => {
        const count = distribution?.[level] ?? 0;
        return (
          <li key={level} className="grid grid-cols-[72px_1fr_28px] items-center gap-2 text-sm">
            <span className="text-muted">{humanize(level)}</span>
            <span className="h-3 rounded-r bg-neutral-soft">
              <span className="block h-full rounded-r bg-warning" style={{ width: `${(count / max) * 100}%` }} />
            </span>
            <span className="text-right font-medium tabular-nums">{count}</span>
          </li>
        );
      })}
    </ul>
  );
}
