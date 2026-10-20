"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Card, ProgressBar, StatCard } from "@/components/feedback/States";
import { StatusBadge } from "@/components/ui/Badge";
import { useAuth } from "@/features/auth/AuthProvider";
import { useProject } from "@/features/projects/api";
import { api } from "@/lib/api/client";
import { formatDate, formatMoney, formatPercent, humanize } from "@/lib/utils/format";

export default function ProjectOverviewPage() {
  const { projectId } = useParams();
  const { me } = useAuth();
  const { data: project } = useProject(projectId);
  const currency = me.activeOrganization.currencyCode;
  const finance = useQuery({
    queryKey: ["finance", "summary", projectId],
    queryFn: () => api.get(`/projects/${projectId}/financial-summary`),
    enabled: Boolean(project?.canViewFinance),
  });
  const milestones = useQuery({
    queryKey: ["milestones", projectId],
    queryFn: () => api.get(`/projects/${projectId}/milestones`),
  });

  if (!project) return null;
  const details = [
    ["Client", project.clientName],
    ["Client contact", project.clientContact ?? "-"],
    ["Site", project.siteAddress],
    ["Project manager", project.manager?.fullName],
    ["Start", formatDate(project.startDate)],
    ["Planned completion", formatDate(project.plannedEndDate)],
    ["Actual completion", formatDate(project.actualEndDate)],
    ["Your project role", project.myProjectRole ? humanize(project.myProjectRole) : "Organization access"],
  ];

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="space-y-6 lg:col-span-2">
        <div className="grid gap-4 sm:grid-cols-3">
          <StatCard label="Progress" value={`${project.progressPercent}%`} hint="Weighted by task importance" />
          <StatCard label="Approved budget" value={formatMoney(project.approvedBudget, currency, { compact: true })} tone="brand" />
          {project.canViewFinance && (
            <StatCard
              label="Budget used"
              value={formatPercent(finance.data?.utilizationPercent)}
              loading={finance.isPending}
              hint={finance.data ? `${formatMoney(finance.data.pendingSpend, currency, { compact: true })} awaiting approval` : undefined}
              tone={Number(finance.data?.utilizationPercent) > 90 ? "danger" : "success"}
            />
          )}
        </div>
        <Card title="Details" bodyClassName="p-4">
          {project.statusReason && (
            <p className="mb-4 rounded-md bg-warning-soft px-3 py-2 text-sm text-warning">
              Status note: {project.statusReason}
            </p>
          )}
          <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
            {details.map(([label, value]) => (
              <div key={label}>
                <dt className="text-xs font-medium uppercase tracking-wide text-muted">{label}</dt>
                <dd className="mt-0.5 text-sm">{value}</dd>
              </div>
            ))}
          </dl>
          {project.description && <p className="mt-4 whitespace-pre-line border-t border-border pt-4 text-sm text-muted">{project.description}</p>}
        </Card>
      </div>
      <Card title="Milestones" bodyClassName="p-4">
        {milestones.data?.length ? (
          <ul className="space-y-4">
            {milestones.data.map((m) => (
              <li key={m.id}>
                <div className="flex items-center justify-between gap-2 text-sm">
                  <span className="font-medium">{m.name}</span>
                  <StatusBadge status={m.status === "COMPLETED" ? "DONE" : m.overdue ? "OVERDUE" : "PENDING"} />
                </div>
                <p className={`mb-1 text-xs ${m.overdue ? "text-danger" : "text-muted"}`}>Due {formatDate(m.dueDate)}</p>
                <ProgressBar value={m.progressPercent} label={`${m.name} progress`} />
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-muted">No milestones yet.</p>
        )}
      </Card>
    </div>
  );
}
