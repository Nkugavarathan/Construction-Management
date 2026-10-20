"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, ErrorState, LoadingSkeleton, StatCard } from "@/components/feedback/States";
import { DataTable, Pagination } from "@/components/ui/DataTable";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { useAuth } from "@/features/auth/AuthProvider";
import { StockMovementDialog } from "@/features/inventory/StockMovementDialog";
import { api } from "@/lib/api/client";
import { usePagedQuery } from "@/lib/api/hooks";
import { formatDateTime, formatNumber, humanize } from "@/lib/utils/format";
import { cn } from "@/lib/utils/cn";

export default function MaterialPage() {
  const { materialId } = useParams();
  const { can } = useAuth();
  const [movement, setMovement] = useState(null);
  const material = useQuery({ queryKey: ["materials", "detail", materialId], queryFn: () => api.get(`/materials/${materialId}`) });
  const ledger = usePagedQuery(["movements", materialId], `/materials/${materialId}/movements`, { size: 25, initialSort: "occurredAt,desc" });

  if (material.error) return <Card><ErrorState error={material.error} onRetry={material.refetch} /></Card>;
  if (material.isPending) return <LoadingSkeleton rows={6} />;
  const m = material.data;

  const columns = [
    { header: "When", meta: { sortField: "occurredAt" }, cell: ({ row }) => formatDateTime(row.original.occurredAt) },
    { header: "Type", cell: ({ row }) => <Badge tone={row.original.quantity > 0 ? "success" : "warning"}>{humanize(row.original.type)}</Badge> },
    { header: "Quantity", meta: { className: "text-right" }, cell: ({ row }) => (
      <span className={cn("block text-right font-medium tabular-nums", Number(row.original.quantity) < 0 ? "text-danger" : "text-success")}>
        {Number(row.original.quantity) > 0 ? "+" : ""}{formatNumber(row.original.quantity)}
      </span>) },
    { header: "Balance", meta: { className: "text-right" }, cell: ({ row }) => <span className="block text-right tabular-nums">{formatNumber(row.original.balanceAfter)}</span> },
    { header: "Project / reference", cell: ({ row }) => row.original.project ? `${row.original.project.code}` : row.original.referenceType ? humanize(row.original.referenceType) : "—" },
    { header: "Reason", cell: ({ row }) => <span className="text-muted">{row.original.reason ?? "—"}</span> },
    { header: "By", cell: ({ row }) => row.original.actor.fullName },
  ];

  return (
    <>
      <PageHeader title={m.name} description={`${m.code} · unit ${m.unit}`} breadcrumbs={[{ label: "Inventory", href: "/inventory" }, { label: m.code }]}
        meta={m.lowStock ? <Badge tone="warning">Low stock</Badge> : null}
        actions={<>
          {can("INVENTORY_RECEIVE") && <Button variant="outline" onClick={() => setMovement("receipt")}>Receive</Button>}
          {can("INVENTORY_ISSUE") && <Button variant="outline" onClick={() => setMovement("issue")}>Issue</Button>}
          {can("INVENTORY_ADJUST") && <Button variant="outline" onClick={() => setMovement("adjustment")}>Adjust</Button>}
        </>} />
      <div className="mb-6 grid gap-4 sm:grid-cols-2">
        <StatCard label="On hand" value={`${formatNumber(m.quantityOnHand)} ${m.unit}`} tone={m.lowStock ? "warning" : "success"} />
        <StatCard label="Reorder level" value={`${formatNumber(m.reorderLevel)} ${m.unit}`} />
      </div>
      <Card title="Stock ledger" description="Append-only: every receipt, issue and adjustment with the running balance.">
        <DataTable columns={columns} data={ledger.rows} loading={ledger.isPending} error={ledger.error} onRetry={ledger.refetch} sort={ledger.sort} onSortChange={ledger.setSort} />
        <Pagination page={ledger.page} size={ledger.size} totalPages={ledger.data?.totalPages} totalElements={ledger.data?.totalElements} onPageChange={ledger.setPage} />
      </Card>
      {movement && <StockMovementDialog kind={movement} material={m} onClose={() => setMovement(null)} />}
    </>
  );
}
