"use client";

import { Fragment, useState } from "react";
import { ChevronDown, ChevronRight, ScrollText } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, EmptyState, ErrorState, LoadingSkeleton } from "@/components/feedback/States";
import { Pagination } from "@/components/ui/DataTable";
import { FilterBar } from "@/components/ui/FilterBar";
import { Badge } from "@/components/ui/Badge";
import { Input, Select } from "@/components/forms/Fields";
import { usePagedQuery } from "@/lib/api/hooks";
import { formatDateTime, humanize } from "@/lib/utils/format";

const ACTIONS = [
  "ORGANIZATION_SETTINGS_CHANGED", "MEMBER_ADDED", "MEMBER_ROLE_CHANGED", "MEMBER_STATUS_CHANGED", "PASSWORD_CHANGED",
  "PROJECT_CREATED", "PROJECT_UPDATED", "PROJECT_STATUS_CHANGED", "PROJECT_MEMBER_ADDED", "PROJECT_MEMBER_REMOVED",
  "TASK_CREATED", "TASK_ASSIGNED", "TASK_STATUS_CHANGED", "BUDGET_ALLOCATION_CHANGED", "EXPENSE_SUBMITTED",
  "EXPENSE_APPROVED", "EXPENSE_REJECTED", "EXPENSE_REVERSAL_CREATED", "INVENTORY_RECEIVED", "INVENTORY_ISSUED",
  "INVENTORY_ADJUSTED", "PURCHASE_ORDER_CREATED", "PURCHASE_ORDER_STATUS_CHANGED", "GOODS_RECEIVED",
  "DOCUMENT_UPLOADED", "DOCUMENT_VERSION_UPLOADED", "RISK_CREATED", "RISK_UPDATED", "INCIDENT_REPORTED", "INCIDENT_STATUS_CHANGED",
];

/** Read-only, append-only history of important changes. Nothing on this page can modify it. */
export default function AuditPage() {
  const [action, setAction] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [expanded, setExpanded] = useState(null);
  const filters = {
    action,
    from: from ? new Date(`${from}T00:00:00`).toISOString() : undefined,
    to: to ? new Date(`${to}T23:59:59`).toISOString() : undefined,
  };
  const list = usePagedQuery(["audit"], "/audit-events", { filters, size: 25 });

  return (
    <>
      <PageHeader title="Audit log" description="Who changed what, and when. Entries cannot be edited or deleted." />
      <Card>
        <FilterBar>
          <Select aria-label="Action" className="w-full sm:w-64" value={action} onChange={(e) => { setAction(e.target.value); list.setPage(0); }} options={ACTIONS} placeholder="All actions" />
          <label className="flex items-center gap-2 text-sm text-muted">From <Input type="date" className="w-40" value={from} onChange={(e) => { setFrom(e.target.value); list.setPage(0); }} /></label>
          <label className="flex items-center gap-2 text-sm text-muted">To <Input type="date" className="w-40" value={to} onChange={(e) => { setTo(e.target.value); list.setPage(0); }} /></label>
        </FilterBar>
        {list.error ? <ErrorState error={list.error} onRetry={list.refetch} /> : list.isPending ? <LoadingSkeleton /> : list.rows.length === 0 ? (
          <EmptyState icon={ScrollText} title="No audit events match" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-sm">
              <thead>
                <tr className="border-b border-border bg-surface-muted text-left text-xs uppercase tracking-wide text-muted">
                  <th scope="col" className="w-8 px-2 py-2"><span className="sr-only">Expand</span></th>
                  <th scope="col" className="px-3 py-2">When</th>
                  <th scope="col" className="px-3 py-2">Action</th>
                  <th scope="col" className="px-3 py-2">Summary</th>
                  <th scope="col" className="px-3 py-2">Actor</th>
                </tr>
              </thead>
              <tbody>
                {list.rows.map((e) => (
                  <Fragment key={e.id}>
                    <tr className="border-b border-border">
                      <td className="px-2 py-2.5">
                        {e.changes && (
                          <button type="button" onClick={() => setExpanded(expanded === e.id ? null : e.id)} aria-expanded={expanded === e.id} aria-label="Show changes" className="rounded p-1 text-muted hover:bg-neutral-soft">
                            {expanded === e.id ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                          </button>
                        )}
                      </td>
                      <td className="whitespace-nowrap px-3 py-2.5 text-muted">{formatDateTime(e.createdAt)}</td>
                      <td className="px-3 py-2.5"><Badge>{humanize(e.action)}</Badge></td>
                      <td className="px-3 py-2.5">{e.summary}</td>
                      <td className="px-3 py-2.5">{e.actor?.fullName ?? "System"}</td>
                    </tr>
                    {expanded === e.id && (
                      <tr className="border-b border-border bg-surface-muted">
                        <td colSpan={5} className="px-4 py-3">
                          <pre className="overflow-x-auto whitespace-pre-wrap text-xs">{JSON.stringify(JSON.parse(e.changes), null, 2)}</pre>
                          <p className="mt-2 text-xs text-muted">{e.entityType} {e.entityId} · request {e.correlationId}</p>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Pagination page={list.page} size={list.size} totalPages={list.data?.totalPages} totalElements={list.data?.totalElements} onPageChange={list.setPage} />
      </Card>
    </>
  );
}
