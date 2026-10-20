"use client";

import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ArrowDownToLine, ArrowUpFromLine, Package, Plus, SlidersHorizontal } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, EmptyState } from "@/components/feedback/States";
import { DataTable, Pagination } from "@/components/ui/DataTable";
import { FilterBar, SearchField } from "@/components/ui/FilterBar";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { Checkbox, SelectField, TextField } from "@/components/forms/Fields";
import { useAuth } from "@/features/auth/AuthProvider";
import { StockMovementDialog } from "@/features/inventory/StockMovementDialog";
import { api, applyServerErrors } from "@/lib/api/client";
import { useApiMutation, usePagedQuery } from "@/lib/api/hooks";
import { MATERIAL_UNITS } from "@/lib/constants/status";
import { formatNumber } from "@/lib/utils/format";

const schema = z.object({
  code: z.string().trim().regex(/^[A-Za-z0-9-]{2,32}$/, "2–32 letters, digits or dashes"),
  name: z.string().trim().min(1, "Required").max(160),
  unit: z.enum(MATERIAL_UNITS),
  reorderLevel: z.string().trim().regex(/^\d{1,11}(\.\d{1,3})?$/, "A number with up to 3 decimals"),
});

export default function InventoryPage() {
  const { can } = useAuth();
  const [q, setQ] = useState("");
  const [lowStock, setLowStock] = useState(false);
  const [creating, setCreating] = useState(false);
  const [movement, setMovement] = useState(null);
  const list = usePagedQuery(["materials", "list"], "/materials", { filters: { q, lowStock: lowStock || undefined }, initialSort: "code,asc" });

  const columns = [
    { header: "Material", meta: { sortField: "code" }, cell: ({ row }) => (
      <div><Link href={`/inventory/${row.original.id}`} className="font-medium hover:underline" onClick={(e) => e.stopPropagation()}>{row.original.name}</Link>
        <p className="text-xs text-muted">{row.original.code}</p></div>) },
    { header: "On hand", meta: { sortField: "quantityOnHand", className: "text-right" }, cell: ({ row }) => (
      <div className="text-right"><span className="font-medium tabular-nums">{formatNumber(row.original.quantityOnHand)}</span> <span className="text-xs text-muted">{row.original.unit}</span></div>) },
    { header: "Reorder at", meta: { className: "text-right" }, cell: ({ row }) => <span className="block text-right tabular-nums text-muted">{formatNumber(row.original.reorderLevel)}</span> },
    { header: "Stock", cell: ({ row }) => row.original.lowStock ? <Badge tone="warning">Low stock</Badge> : <Badge tone="success">OK</Badge> },
    {
      header: <span className="sr-only">Actions</span>,
      id: "actions",
      cell: ({ row }) => (
        <div className="flex justify-end gap-1" onClick={(e) => e.stopPropagation()}>
          {can("INVENTORY_RECEIVE") && <Button size="sm" variant="ghost" title="Receive" aria-label={`Receive ${row.original.code}`} onClick={() => setMovement({ kind: "receipt", material: row.original })}><ArrowDownToLine className="h-4 w-4" /></Button>}
          {can("INVENTORY_ISSUE") && <Button size="sm" variant="ghost" title="Issue" aria-label={`Issue ${row.original.code}`} onClick={() => setMovement({ kind: "issue", material: row.original })}><ArrowUpFromLine className="h-4 w-4" /></Button>}
          {can("INVENTORY_ADJUST") && <Button size="sm" variant="ghost" title="Adjust" aria-label={`Adjust ${row.original.code}`} onClick={() => setMovement({ kind: "adjustment", material: row.original })}><SlidersHorizontal className="h-4 w-4" /></Button>}
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader title="Inventory" description="Material catalogue and stock on hand. Every change is written to the stock ledger."
        actions={can("MATERIAL_MANAGE") && <Button onClick={() => setCreating(true)}><Plus className="h-4 w-4" aria-hidden="true" /> New material</Button>} />
      <Card>
        <FilterBar>
          <SearchField value={q} onChange={(v) => { setQ(v); list.setPage(0); }} placeholder="Search code or name" />
          <Checkbox label="Low stock only" checked={lowStock} onChange={(e) => { setLowStock(e.target.checked); list.setPage(0); }} />
        </FilterBar>
        <DataTable columns={columns} data={list.rows} loading={list.isPending} error={list.error} onRetry={list.refetch}
          sort={list.sort} onSortChange={list.setSort}
          empty={<EmptyState icon={Package} title={lowStock ? "Nothing is low on stock" : "No materials yet"} />} />
        <Pagination page={list.page} size={list.size} totalPages={list.data?.totalPages} totalElements={list.data?.totalElements} onPageChange={list.setPage} />
      </Card>
      {movement && <StockMovementDialog kind={movement.kind} material={movement.material} onClose={() => setMovement(null)} />}
      <Dialog open={creating} onClose={() => setCreating(false)} title="New material">
        {creating && <MaterialForm onDone={() => setCreating(false)} />}
      </Dialog>
    </>
  );
}

function MaterialForm({ onDone }) {
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(schema), defaultValues: { code: "", name: "", unit: "BAG", reorderLevel: "0" },
  });
  const create = useApiMutation((body) => api.post("/materials", body), { invalidate: [["materials"]], success: "Material created", onSuccess: onDone });
  return (
    <form className="grid gap-4 sm:grid-cols-2" noValidate onSubmit={handleSubmit(async (v) => {
      try { await create.mutateAsync(v); } catch (e) { applyServerErrors(e, setError); }
    })}>
      <TextField label="Code" placeholder="CEM-OPC-50" register={register("code")} error={errors.code?.message} required />
      <SelectField label="Unit" options={MATERIAL_UNITS} humanizeLabels={false} register={register("unit")} error={errors.unit?.message} required />
      <TextField label="Name" register={register("name")} error={errors.name?.message} required className="sm:col-span-2" />
      <TextField label="Reorder level" inputMode="decimal" hint="You'll be alerted when stock falls to this level." register={register("reorderLevel")} error={errors.reorderLevel?.message} required />
      <div className="flex justify-end gap-2 sm:col-span-2">
        <Button variant="outline" onClick={onDone}>Cancel</Button>
        <Button type="submit" loading={isSubmitting}>Create</Button>
      </div>
    </form>
  );
}
