"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { ReceiptText } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, EmptyState } from "@/components/feedback/States";
import { DataTable, Pagination } from "@/components/ui/DataTable";
import { FilterBar } from "@/components/ui/FilterBar";
import { Checkbox, Select } from "@/components/forms/Fields";
import { useAuth } from "@/features/auth/AuthProvider";
import { ExpenseDialog } from "@/features/finance/ExpenseDialog";
import { expenseColumns } from "@/features/finance/expenseColumns";
import { usePagedQuery } from "@/lib/api/hooks";
import { EXPENSE_STATUSES } from "@/lib/constants/status";

/**
 * Organization-wide expense list. Approvers land on the "Submitted" queue; what each person sees is
 * decided by the API (finance and admins: everything, project managers: their projects, others: own).
 */
export default function ExpensesPage() {
  const { me, can } = useAuth();
  const searchParams = useSearchParams();
  const [status, setStatus] = useState(searchParams.get("status") ?? (can("EXPENSE_APPROVE") ? "SUBMITTED" : ""));
  const [mine, setMine] = useState(false);
  const [openId, setOpenId] = useState(searchParams.get("expense"));
  const list = usePagedQuery(["expenses", "org"], "/expenses", { filters: { status, mine: mine || undefined }, initialSort: "createdAt,desc" });

  return (
    <>
      <PageHeader title="Expenses" description={can("EXPENSE_APPROVE") ? "Review submitted expenses and track spending across projects." : "Expenses you have recorded."} />
      <Card>
        <FilterBar>
          <Select aria-label="Status" className="w-full sm:w-44" value={status} onChange={(e) => { setStatus(e.target.value); list.setPage(0); }} options={EXPENSE_STATUSES} placeholder="All statuses" />
          <Checkbox label="Only mine" checked={mine} onChange={(e) => { setMine(e.target.checked); list.setPage(0); }} />
        </FilterBar>
        <DataTable columns={expenseColumns(me.activeOrganization.currencyCode, { showProject: true })} data={list.rows} loading={list.isPending}
          error={list.error} onRetry={list.refetch} sort={list.sort} onSortChange={list.setSort} onRowClick={(row) => setOpenId(row.id)}
          empty={<EmptyState icon={ReceiptText} title={status === "SUBMITTED" ? "Nothing waiting for approval" : "No expenses found"} />} />
        <Pagination page={list.page} size={list.size} totalPages={list.data?.totalPages} totalElements={list.data?.totalElements} onPageChange={list.setPage} />
      </Card>
      <ExpenseDialog expenseId={openId} onClose={() => setOpenId(null)} />
    </>
  );
}
