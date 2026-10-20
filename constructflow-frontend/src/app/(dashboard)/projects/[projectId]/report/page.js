"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Printer } from "lucide-react";
import { Card, ErrorState, LoadingSkeleton, ProgressBar } from "@/components/feedback/States";
import { StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { useAuth } from "@/features/auth/AuthProvider";
import { api } from "@/lib/api/client";
import { formatDate, formatDateTime, formatMoney, formatNumber, formatPercent, humanize } from "@/lib/utils/format";

/**
 * Project status report built entirely from GET /reports/projects/{id}. Every figure is a verified
 * value from the database; nothing here is generated or estimated.
 */
export default function ProjectReportPage() {
  const { projectId } = useParams();
  const { me } = useAuth();
  const currency = me.activeOrganization.currencyCode;
  const { data: r, error, isPending, refetch } = useQuery({
    queryKey: ["report", projectId],
    queryFn: () => api.get(`/reports/projects/${projectId}`),
  });

  if (error) return <Card><ErrorState error={error} onRetry={refetch} /></Card>;
  if (isPending) return <LoadingSkeleton rows={8} />;
  const p = r.project;

  return (
    <article className="space-y-6 print:space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted">Generated {formatDateTime(r.generatedAt)} from live project data.</p>
        <Button variant="outline" size="sm" onClick={() => window.print()} className="print:hidden">
          <Printer className="h-4 w-4" aria-hidden="true" /> Print / save as PDF
        </Button>
      </div>

      <Card title="Summary" bodyClassName="grid gap-4 p-4 sm:grid-cols-3">
        <div><p className="text-xs uppercase text-muted">Status</p><StatusBadge status={p.status} /></div>
        <div><p className="text-xs uppercase text-muted">Schedule</p><p className="text-sm">{formatDate(p.startDate)} → {formatDate(p.plannedEndDate)}</p></div>
        <div><p className="text-xs uppercase text-muted">Overall progress</p><ProgressBar value={p.progressPercent} /></div>
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Tasks by status" bodyClassName="p-4">
          <ul className="grid grid-cols-2 gap-2 text-sm">
            {Object.entries(r.tasksByStatus).map(([status, count]) => (
              <li key={status} className="flex items-center justify-between rounded-md bg-surface-muted px-3 py-2">
                <span>{humanize(status)}</span><span className="font-semibold tabular-nums">{count}</span>
              </li>
            ))}
          </ul>
        </Card>
        <Card title={`Overdue tasks (${r.overdueTasks.length})`} bodyClassName="p-4">
          {r.overdueTasks.length === 0 ? <p className="text-sm text-muted">No overdue tasks.</p> : (
            <ul className="space-y-2 text-sm">
              {r.overdueTasks.map((t) => (
                <li key={t.id} className="flex justify-between gap-2">
                  <span>{t.title} <span className="text-muted">· {t.assignee?.fullName ?? "Unassigned"}</span></span>
                  <span className="whitespace-nowrap text-danger">{formatDate(t.dueDate)}</span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      <Card title="Milestones" bodyClassName="p-4">
        {r.milestones.length === 0 ? <p className="text-sm text-muted">No milestones.</p> : (
          <ul className="space-y-3">
            {r.milestones.map((m) => (
              <li key={m.id} className="grid gap-2 text-sm sm:grid-cols-[1fr_120px_200px] sm:items-center">
                <span className="font-medium">{m.name}</span>
                <span className={m.overdue ? "text-danger" : "text-muted"}>{formatDate(m.dueDate)}</span>
                <ProgressBar value={m.progressPercent} label={`${m.name} progress`} />
              </li>
            ))}
          </ul>
        )}
      </Card>

      {r.finance && (
        <Card title="Financial position" bodyClassName="p-4">
          <dl className="grid gap-4 text-sm sm:grid-cols-4">
            <div><dt className="text-xs uppercase text-muted">Budget</dt><dd className="font-semibold">{formatMoney(r.finance.approvedBudget, currency)}</dd></div>
            <div><dt className="text-xs uppercase text-muted">Approved spend</dt><dd className="font-semibold">{formatMoney(r.finance.approvedSpend, currency)}</dd></div>
            <div><dt className="text-xs uppercase text-muted">Pending</dt><dd className="font-semibold">{formatMoney(r.finance.pendingSpend, currency)}</dd></div>
            <div><dt className="text-xs uppercase text-muted">Utilization</dt><dd className="font-semibold">{formatPercent(r.finance.utilizationPercent)}</dd></div>
          </dl>
        </Card>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Top open risks" bodyClassName="p-4">
          {r.topRisks.length === 0 ? <p className="text-sm text-muted">No open risks.</p> : (
            <ul className="space-y-2 text-sm">
              {r.topRisks.map((risk) => (
                <li key={risk.id} className="flex items-center justify-between gap-2">
                  <span>{risk.title}</span>
                  <span className="flex items-center gap-2"><span className="tabular-nums">{risk.score}</span><StatusBadge status={risk.level} /></span>
                </li>
              ))}
            </ul>
          )}
          <p className="mt-3 text-xs text-muted">{r.openIncidents} open safety incident(s).</p>
        </Card>
        <Card title="Materials issued to this project" bodyClassName="p-4">
          {r.materialUsage.length === 0 ? <p className="text-sm text-muted">No materials issued.</p> : (
            <ul className="space-y-2 text-sm">
              {r.materialUsage.map((u) => (
                <li key={u.materialId} className="flex justify-between gap-2">
                  <span>{u.name} <span className="text-muted">({u.code})</span></span>
                  <span className="tabular-nums">{formatNumber(u.quantity)} {u.unit}</span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </article>
  );
}
