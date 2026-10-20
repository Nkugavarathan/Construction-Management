"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { FolderKanban, Plus } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, EmptyState, ProgressBar } from "@/components/feedback/States";
import { DataTable, Pagination } from "@/components/ui/DataTable";
import { FilterBar, SearchField } from "@/components/ui/FilterBar";
import { Select } from "@/components/forms/Fields";
import { StatusBadge } from "@/components/ui/Badge";
import { useAuth } from "@/features/auth/AuthProvider";
import { usePagedQuery } from "@/lib/api/hooks";
import { PROJECT_STATUSES } from "@/lib/constants/status";
import { formatDate, formatMoney, formatPercent } from "@/lib/utils/format";

export default function ProjectsPage() {
  const { can, me } = useAuth();
  const router = useRouter();
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("");
  const currency = me.activeOrganization.currencyCode;
  const list = usePagedQuery(["projects", "list"], "/projects", { filters: { q, status }, initialSort: "createdAt,desc" });

  const columns = [
    {
      header: "Project",
      meta: { sortField: "name" },
      cell: ({ row }) => (
        <div className="min-w-0">
          <Link href={`/projects/${row.original.id}`} className="font-medium hover:underline" onClick={(e) => e.stopPropagation()}>
            {row.original.name}
          </Link>
          <p className="text-xs text-muted">{row.original.code} · {row.original.clientName}</p>
        </div>
      ),
    },
    { header: "Status", meta: { sortField: "status" }, cell: ({ row }) => <StatusBadge status={row.original.status} /> },
    { header: "Manager", cell: ({ row }) => row.original.manager?.fullName },
    { header: "Due", meta: { sortField: "plannedEndDate" }, cell: ({ row }) => formatDate(row.original.plannedEndDate) },
    { header: "Progress", meta: { className: "w-40" }, cell: ({ row }) => <ProgressBar value={row.original.progressPercent} label={`${row.original.name} progress`} /> },
    {
      header: "Budget",
      meta: { sortField: "approvedBudget", className: "text-right" },
      cell: ({ row }) => (
        <div className="text-right">
          <p className="tabular-nums">{formatMoney(row.original.approvedBudget, currency, { compact: true })}</p>
          {row.original.budgetUtilizationPercent !== null && (
            <p className="text-xs text-muted">{formatPercent(row.original.budgetUtilizationPercent)} spent</p>
          )}
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Projects"
        description="All projects you can access in this organization."
        actions={can("PROJECT_CREATE") && (
          <Link href="/projects/new" className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-white hover:bg-primary-hover">
            <Plus className="h-4 w-4" aria-hidden="true" /> New project
          </Link>
        )}
      />
      <Card>
        <FilterBar>
          <SearchField value={q} onChange={(v) => { setQ(v); list.setPage(0); }} placeholder="Search name, code or client" />
          <Select aria-label="Status" className="w-full sm:w-44" value={status} onChange={(e) => { setStatus(e.target.value); list.setPage(0); }} options={PROJECT_STATUSES} placeholder="All statuses" />
        </FilterBar>
        <DataTable
          columns={columns}
          data={list.rows}
          loading={list.isPending}
          error={list.error}
          onRetry={list.refetch}
          sort={list.sort}
          onSortChange={list.setSort}
          onRowClick={(row) => router.push(`/projects/${row.id}`)}
          empty={
            <EmptyState
              icon={FolderKanban}
              title={q || status ? "No projects match your filters" : "No projects yet"}
              description={q || status ? "Try a different search or status." : "Create your first project to start tracking work and costs."}
            />
          }
        />
        <Pagination page={list.page} size={list.size} totalPages={list.data?.totalPages} totalElements={list.data?.totalElements} onPageChange={list.setPage} />
      </Card>
    </>
  );
}
