"use client";

import Link from "next/link";
import { useParams, usePathname } from "next/navigation";
import { Card, ErrorState, LoadingSkeleton } from "@/components/feedback/States";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatusBadge } from "@/components/ui/Badge";
import { useAuth } from "@/features/auth/AuthProvider";
import { useProject } from "@/features/projects/api";
import { ProjectActions } from "@/features/projects/ProjectActions";
import { cn } from "@/lib/utils/cn";

const TABS = [
  { segment: "", label: "Overview" },
  { segment: "tasks", label: "Tasks" },
  { segment: "milestones", label: "Milestones" },
  { segment: "site-reports", label: "Site reports", permission: "SITE_REPORT_READ" },
  { segment: "finance", label: "Finance", financeOnly: true },
  { segment: "documents", label: "Documents", permission: "DOCUMENT_READ" },
  { segment: "risks", label: "Risks", permission: "RISK_READ" },
  { segment: "safety", label: "Safety", permission: "INCIDENT_READ" },
  { segment: "members", label: "Team" },
  { segment: "report", label: "Report", permission: "REPORT_READ" },
];

/** Shared header and tab bar for every project page; each tab is its own route. */
export default function ProjectLayout({ children }) {
  const { projectId } = useParams();
  const pathname = usePathname();
  const { can } = useAuth();
  const { data: project, error, isPending, refetch } = useProject(projectId);

  if (error) return <Card><ErrorState error={error} onRetry={refetch} /></Card>;
  if (isPending) return <LoadingSkeleton rows={6} />;

  const base = `/projects/${projectId}`;
  // Engineers record expenses but only see their own, so the tab shows for them without the budget summary.
  const tabs = TABS.filter((t) => (t.financeOnly
    ? project.canViewFinance || can("EXPENSE_CREATE")
    : !t.permission || can(t.permission)));

  return (
    <>
      <PageHeader
        title={project.name}
        meta={<StatusBadge status={project.status} />}
        description={`${project.code} · ${project.clientName} · ${project.siteAddress}`}
        breadcrumbs={[{ label: "Projects", href: "/projects" }, { label: project.code }]}
        actions={<ProjectActions project={project} />}
      />
      <nav aria-label="Project sections" className="-mx-4 mb-6 overflow-x-auto border-b border-border px-4 lg:mx-0 lg:px-0">
        <ul className="flex gap-1">
          {tabs.map((tab) => {
            const href = tab.segment ? `${base}/${tab.segment}` : base;
            const active = tab.segment ? pathname.startsWith(href) : pathname === base;
            return (
              <li key={tab.label}>
                <Link
                  href={href}
                  aria-current={active ? "page" : undefined}
                  className={cn(
                    "inline-block whitespace-nowrap border-b-2 px-3 py-2 text-sm font-medium",
                    active ? "border-secondary text-text" : "border-transparent text-muted hover:text-text",
                  )}
                >
                  {tab.label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>
      {children}
    </>
  );
}
