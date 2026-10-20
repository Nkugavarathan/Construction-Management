"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Plus, ShoppingCart, Truck } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, EmptyState } from "@/components/feedback/States";
import { DataTable, Pagination } from "@/components/ui/DataTable";
import { FilterBar, SearchField } from "@/components/ui/FilterBar";
import { Badge, StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { FormField, Input, Select } from "@/components/forms/Fields";
import { useAuth } from "@/features/auth/AuthProvider";
import { api } from "@/lib/api/client";
import { useApiMutation, usePagedQuery } from "@/lib/api/hooks";
import { PO_STATUSES } from "@/lib/constants/status";
import { cn } from "@/lib/utils/cn";
import { formatDate, formatMoney } from "@/lib/utils/format";

export default function ProcurementPage() {
  const { can, me } = useAuth();
  const router = useRouter();
  const [tab, setTab] = useState("orders");
  const [status, setStatus] = useState("");
  const currency = me.activeOrganization.currencyCode;
  const orders = usePagedQuery(["purchase-orders", "list"], "/purchase-orders", { filters: { status }, initialSort: "createdAt,desc", enabled: tab === "orders" });

  const columns = [
    { header: "Order", meta: { sortField: "number" }, cell: ({ row }) => (<div><p className="font-medium">{row.original.number}</p><p className="text-xs text-muted">{row.original.supplier.name}</p></div>) },
    { header: "Project", cell: ({ row }) => row.original.project?.code ?? <span className="text-muted">Stock</span> },
    { header: "Status", meta: { sortField: "status" }, cell: ({ row }) => <StatusBadge status={row.original.status} /> },
    { header: "Expected", meta: { sortField: "expectedDate" }, cell: ({ row }) => formatDate(row.original.expectedDate) },
    { header: "Created by", cell: ({ row }) => row.original.createdBy.fullName },
    { header: "Total", meta: { sortField: "totalAmount", className: "text-right" }, cell: ({ row }) => <span className="block text-right font-medium tabular-nums">{formatMoney(row.original.totalAmount, currency)}</span> },
  ];

  return (
    <>
      <PageHeader title="Procurement" description="Purchase orders move from draft to approval to receiving. Receipts update stock automatically."
        actions={can("PO_CREATE") && (
          <Link href="/procurement/new" className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-white hover:bg-primary-hover">
            <Plus className="h-4 w-4" aria-hidden="true" /> New purchase order
          </Link>
        )} />
      <div className="mb-4 flex gap-1 border-b border-border" role="tablist" aria-label="Procurement">
        {[["orders", "Purchase orders"], ["suppliers", "Suppliers"]].map(([key, label]) => (
          <button key={key} role="tab" aria-selected={tab === key} onClick={() => setTab(key)}
            className={cn("border-b-2 px-3 py-2 text-sm font-medium", tab === key ? "border-secondary" : "border-transparent text-muted hover:text-text")}>
            {label}
          </button>
        ))}
      </div>
      {tab === "orders" ? (
        <Card>
          <FilterBar>
            <Select aria-label="Status" className="w-full sm:w-52" value={status} onChange={(e) => { setStatus(e.target.value); orders.setPage(0); }} options={PO_STATUSES} placeholder="All statuses" />
          </FilterBar>
          <DataTable columns={columns} data={orders.rows} loading={orders.isPending} error={orders.error} onRetry={orders.refetch}
            sort={orders.sort} onSortChange={orders.setSort} onRowClick={(row) => router.push(`/procurement/${row.id}`)}
            empty={<EmptyState icon={ShoppingCart} title="No purchase orders" />} />
          <Pagination page={orders.page} size={orders.size} totalPages={orders.data?.totalPages} totalElements={orders.data?.totalElements} onPageChange={orders.setPage} />
        </Card>
      ) : <Suppliers />}
    </>
  );
}

function Suppliers() {
  const { can } = useAuth();
  const [q, setQ] = useState("");
  const [editing, setEditing] = useState(null);
  const list = usePagedQuery(["suppliers"], "/suppliers", { filters: { q, includeInactive: true }, initialSort: "name,asc" });
  const columns = [
    { header: "Supplier", meta: { sortField: "name" }, cell: ({ row }) => (<div><p className="font-medium">{row.original.name}</p><p className="text-xs text-muted">{row.original.address ?? ""}</p></div>) },
    { header: "Contact", cell: ({ row }) => [row.original.contactName, row.original.phone, row.original.email].filter(Boolean).join(" · ") || "-" },
    { header: "Tax ID", cell: ({ row }) => row.original.taxId ?? "-" },
    { header: "Status", cell: ({ row }) => <Badge tone={row.original.active ? "success" : "neutral"}>{row.original.active ? "Active" : "Inactive"}</Badge> },
  ];
  return (
    <Card actions={can("SUPPLIER_MANAGE") && <Button size="sm" onClick={() => setEditing({})}><Plus className="h-4 w-4" aria-hidden="true" /> Add supplier</Button>} title="Suppliers">
      <FilterBar><SearchField value={q} onChange={(v) => { setQ(v); list.setPage(0); }} placeholder="Search suppliers" /></FilterBar>
      <DataTable columns={columns} data={list.rows} loading={list.isPending} error={list.error} onRetry={list.refetch} sort={list.sort} onSortChange={list.setSort}
        onRowClick={can("SUPPLIER_MANAGE") ? (row) => setEditing(row) : undefined} empty={<EmptyState icon={Truck} title="No suppliers yet" />} />
      <Pagination page={list.page} size={list.size} totalPages={list.data?.totalPages} totalElements={list.data?.totalElements} onPageChange={list.setPage} />
      {editing && <SupplierDialog supplier={editing.id ? editing : null} onClose={() => setEditing(null)} />}
    </Card>
  );
}

function SupplierDialog({ supplier, onClose }) {
  const [form, setForm] = useState({
    name: supplier?.name ?? "", contactName: supplier?.contactName ?? "", email: supplier?.email ?? "",
    phone: supplier?.phone ?? "", address: supplier?.address ?? "", taxId: supplier?.taxId ?? "", active: supplier?.active ?? true,
  });
  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.type === "checkbox" ? e.target.checked : e.target.value }));
  const save = useApiMutation(
    () => (supplier ? api.patch(`/suppliers/${supplier.id}`, { ...form, version: supplier.version }) : api.post("/suppliers", form)),
    { invalidate: [["suppliers"]], success: supplier ? "Supplier updated" : "Supplier added", onSuccess: onClose },
  );
  return (
    <Dialog open onClose={onClose} title={supplier ? "Edit supplier" : "Add supplier"}
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button disabled={!form.name.trim()} loading={save.isPending} onClick={() => save.mutate()}>Save</Button></>}>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField label="Name" required className="sm:col-span-2">{(a) => <Input {...a} value={form.name} maxLength={160} onChange={set("name")} />}</FormField>
        <FormField label="Contact person">{(a) => <Input {...a} value={form.contactName} onChange={set("contactName")} />}</FormField>
        <FormField label="Phone">{(a) => <Input {...a} value={form.phone} onChange={set("phone")} />}</FormField>
        <FormField label="Email">{(a) => <Input {...a} type="email" value={form.email} onChange={set("email")} />}</FormField>
        <FormField label="Tax ID">{(a) => <Input {...a} value={form.taxId} onChange={set("taxId")} />}</FormField>
        <FormField label="Address" className="sm:col-span-2">{(a) => <Input {...a} value={form.address} onChange={set("address")} />}</FormField>
        {supplier && <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.active} onChange={set("active")} /> Active</label>}
      </div>
    </Dialog>
  );
}
