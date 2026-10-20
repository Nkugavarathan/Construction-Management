"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Plus, ReceiptText } from "lucide-react";
import { toast } from "sonner";
import { Card, EmptyState, ErrorState, LoadingSkeleton, StatCard } from "@/components/feedback/States";
import { DataTable, Pagination } from "@/components/ui/DataTable";
import { FilterBar } from "@/components/ui/FilterBar";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { Input, Select } from "@/components/forms/Fields";
import { useAuth } from "@/features/auth/AuthProvider";
import { useProject } from "@/features/projects/api";
import { ExpenseDialog } from "@/features/finance/ExpenseDialog";
import { ExpenseForm, useBudgetCategories } from "@/features/finance/ExpenseForm";
import { expenseColumns } from "@/features/finance/expenseColumns";
import { api } from "@/lib/api/client";
import { useApiMutation, usePagedQuery } from "@/lib/api/hooks";
import { EXPENSE_STATUSES } from "@/lib/constants/status";
import { cn } from "@/lib/utils/cn";
import { formatMoney, formatPercent } from "@/lib/utils/format";

export default function ProjectFinancePage() {
  const { projectId } = useParams();
  const { me, can } = useAuth();
  const currency = me.activeOrganization.currencyCode;
  const { data: project } = useProject(projectId);
  const [status, setStatus] = useState("");
  const [openId, setOpenId] = useState(null);
  const [creating, setCreating] = useState(false);
  const [allocating, setAllocating] = useState(false);
  const summary = useQuery({
    queryKey: ["finance", "summary", projectId],
    queryFn: () => api.get(`/projects/${projectId}/financial-summary`),
    enabled: Boolean(project?.canViewFinance),
  });
  const list = usePagedQuery(["expenses", "project", projectId], `/projects/${projectId}/expenses`, { filters: { status }, initialSort: "createdAt,desc" });
  const open = project && !["COMPLETED", "CANCELLED"].includes(project.status);
  const canAllocate = open && can("BUDGET_MANAGE") && (me.role !== "PROJECT_MANAGER" || project.canManage);

  const create = useApiMutation((body) => api.post(`/projects/${projectId}/expenses`, body), {
    invalidate: [["expenses"], ["finance"]],
    success: "Draft saved. Submit it when ready.",
    onSuccess: (data) => { setCreating(false); setOpenId(data.id); },
  });

  const s = summary.data;
  return (
    <div className="space-y-6">
      {!project?.canViewFinance ? null : summary.error ? <Card><ErrorState error={summary.error} onRetry={summary.refetch} /></Card> : !s ? <LoadingSkeleton rows={2} /> : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard label="Approved budget" value={formatMoney(s.approvedBudget, currency, { compact: true })} />
            <StatCard label="Approved spend" value={formatMoney(s.approvedSpend, currency, { compact: true })} hint={`${formatPercent(s.utilizationPercent)} of budget`} tone="brand" />
            <StatCard label="Awaiting approval" value={formatMoney(s.pendingSpend, currency, { compact: true })} hint={`Projected ${formatPercent(s.projectedUtilizationPercent)}`} tone="warning" />
            <StatCard label="Remaining" value={formatMoney(s.remainingBudget, currency, { compact: true })} tone={Number(s.remainingBudget) < 0 ? "danger" : "success"} />
          </div>
          <Card title="Budget vs actual by category" description="Variance = allocated − approved. Negative means over budget."
            actions={canAllocate && <Button size="sm" variant="outline" onClick={() => setAllocating(true)}>Edit allocations</Button>}>
            {s.categories.length === 0 ? <EmptyState title="No allocations or spend yet" /> : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[560px] text-sm">
                  <thead>
                    <tr className="border-b border-border bg-surface-muted text-left text-xs uppercase tracking-wide text-muted">
                      <th scope="col" className="px-4 py-2">Category</th>
                      <th scope="col" className="px-4 py-2 text-right">Allocated</th>
                      <th scope="col" className="px-4 py-2 text-right">Approved</th>
                      <th scope="col" className="px-4 py-2 text-right">Pending</th>
                      <th scope="col" className="px-4 py-2 text-right">Variance</th>
                    </tr>
                  </thead>
                  <tbody>
                    {s.categories.map((c) => (
                      <tr key={c.categoryId} className="border-b border-border last:border-0">
                        <th scope="row" className="px-4 py-2.5 text-left font-medium">{c.categoryName}</th>
                        <td className="px-4 py-2.5 text-right tabular-nums">{formatMoney(c.allocated, currency)}</td>
                        <td className="px-4 py-2.5 text-right tabular-nums">{formatMoney(c.approved, currency)}</td>
                        <td className="px-4 py-2.5 text-right tabular-nums text-muted">{formatMoney(c.pending, currency)}</td>
                        <td className={cn("px-4 py-2.5 text-right font-medium tabular-nums", Number(c.variance) < 0 ? "text-danger" : "text-success")}>
                          {formatMoney(c.variance, currency)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </>
      )}

      <Card title="Expenses" actions={open && can("EXPENSE_CREATE") && <Button size="sm" onClick={() => setCreating(true)}><Plus className="h-4 w-4" aria-hidden="true" /> Record expense</Button>}>
        <FilterBar>
          <Select aria-label="Status" className="w-full sm:w-44" value={status} onChange={(e) => { setStatus(e.target.value); list.setPage(0); }} options={EXPENSE_STATUSES} placeholder="All statuses" />
        </FilterBar>
        <DataTable columns={expenseColumns(currency)} data={list.rows} loading={list.isPending} error={list.error} onRetry={list.refetch}
          sort={list.sort} onSortChange={list.setSort} onRowClick={(row) => setOpenId(row.id)}
          empty={<EmptyState icon={ReceiptText} title="No expenses" description="Recorded expenses appear here. Drafts only count once approved." />} />
        <Pagination page={list.page} size={list.size} totalPages={list.data?.totalPages} totalElements={list.data?.totalElements} onPageChange={list.setPage} />
      </Card>

      <ExpenseDialog expenseId={openId} onClose={() => setOpenId(null)} />
      <Dialog open={creating} onClose={() => setCreating(false)} title="Record expense" description="Saved as a draft; submit it for approval from the expense view." size="lg">
        {creating && <ExpenseForm onSubmit={create.mutateAsync} onCancel={() => setCreating(false)} />}
      </Dialog>
      {allocating && s && <AllocationDialog projectId={projectId} summary={s} currency={currency} onClose={() => setAllocating(false)} />}
    </div>
  );
}

function AllocationDialog({ projectId, summary, currency, onClose }) {
  const { data: categories = [] } = useBudgetCategories();
  const [values, setValues] = useState(() => Object.fromEntries(summary.categories.map((c) => [c.categoryId, String(c.allocated)])));
  const save = useApiMutation((body) => api.put(`/projects/${projectId}/budget-allocations`, body), {
    invalidate: [["finance"]], success: "Allocations saved", onSuccess: onClose,
  });
  // Display-only total; the server performs the authoritative check with exact decimals.
  const total = Object.values(values).reduce((sum, v) => sum + (Number(v) || 0), 0);
  const over = total > Number(summary.approvedBudget);

  const submit = () => {
    const invalid = Object.values(values).some((v) => v !== "" && !/^\d{1,13}(\.\d{1,2})?$/.test(v));
    if (invalid) {
      toast.error("Amounts must be positive numbers with up to 2 decimals.");
      return;
    }
    save.mutate({
      allocations: Object.entries(values).filter(([, v]) => v !== "" && Number(v) >= 0).map(([categoryId, amount]) => ({ categoryId, amount })),
    });
  };

  return (
    <Dialog open onClose={onClose} title="Budget allocations" description={`Total may not exceed the approved budget of ${formatMoney(summary.approvedBudget, currency)}.`}
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={submit} loading={save.isPending} disabled={over}>Save</Button></>}>
      <div className="space-y-3">
        {categories.filter((c) => c.active).map((c) => (
          <label key={c.id} className="grid grid-cols-[1fr_180px] items-center gap-3 text-sm">
            <span>{c.name}</span>
            <Input inputMode="decimal" value={values[c.id] ?? ""} placeholder="0.00" aria-label={`${c.name} allocation`}
              onChange={(e) => setValues((prev) => ({ ...prev, [c.id]: e.target.value.trim() }))} />
          </label>
        ))}
        <p className={cn("border-t border-border pt-3 text-right text-sm font-medium", over ? "text-danger" : "")}>
          Total {formatMoney(total, currency)} {over && "- exceeds the approved budget"}
        </p>
      </div>
    </Dialog>
  );
}
