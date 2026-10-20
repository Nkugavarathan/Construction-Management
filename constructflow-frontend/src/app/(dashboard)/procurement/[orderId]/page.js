"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, ErrorState, LoadingSkeleton } from "@/components/feedback/States";
import { StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { FormField, Input, Textarea } from "@/components/forms/Fields";
import { useAuth } from "@/features/auth/AuthProvider";
import { PurchaseOrderForm } from "@/features/procurement/PurchaseOrderForm";
import { api, errorMessage } from "@/lib/api/client";
import { useApiMutation } from "@/lib/api/hooks";
import { formatDate, formatDateTime, formatMoney, formatNumber } from "@/lib/utils/format";

const KEYS = [["purchase-orders"], ["materials"], ["movements"], ["dashboard"]];

export default function PurchaseOrderPage() {
  const { orderId } = useParams();
  const { me } = useAuth();
  const queryClient = useQueryClient();
  const currency = me.activeOrganization.currencyCode;
  const [action, setAction] = useState(null); // "return" | "cancel" | "close"
  const [reason, setReason] = useState("");
  const [editing, setEditing] = useState(false);
  const [receiving, setReceiving] = useState(false);
  const order = useQuery({ queryKey: ["purchase-orders", "detail", orderId], queryFn: () => api.get(`/purchase-orders/${orderId}`) });
  const receipts = useQuery({ queryKey: ["purchase-orders", "receipts", orderId], queryFn: () => api.get(`/purchase-orders/${orderId}/receipts`) });

  const change = useApiMutation(
    ({ path, body }) => api.post(`/purchase-orders/${orderId}/${path}`, body),
    { invalidate: KEYS, success: (data) => `${data.number} is now ${data.status.toLowerCase().replace("_", " ")}`, onSuccess: () => { setAction(null); setReason(""); },
      onError: (e) => e.code === "STALE_VERSION" && order.refetch() },
  );

  if (order.error) return <Card><ErrorState error={order.error} onRetry={order.refetch} /></Card>;
  if (order.isPending) return <LoadingSkeleton rows={6} />;
  const po = order.data;
  const a = po.actions;

  return (
    <>
      <PageHeader title={po.number} meta={<StatusBadge status={po.status} />}
        description={`${po.supplier.name}${po.project ? ` · for ${po.project.code}` : " · general stock"} · created by ${po.createdBy.fullName}`}
        breadcrumbs={[{ label: "Procurement", href: "/procurement" }, { label: po.number }]}
        actions={<>
          {a.edit && <Button variant="outline" onClick={() => setEditing(true)}>Edit</Button>}
          {a.submit && <Button loading={change.isPending} onClick={() => change.mutate({ path: "submit", body: { version: po.version } })}>Submit for approval</Button>}
          {a.approve && <Button variant="secondary" loading={change.isPending} onClick={() => change.mutate({ path: "approve", body: { version: po.version } })}>Approve</Button>}
          {a.returnToDraft && <Button variant="outline" onClick={() => setAction("return")}>Return for changes</Button>}
          {a.receive && <Button onClick={() => setReceiving(true)}>Receive delivery</Button>}
          {a.close && <Button variant="outline" onClick={() => setAction("close")}>Close order</Button>}
          {a.cancel && <Button variant="ghost" onClick={() => setAction("cancel")}>Cancel order</Button>}
        </>} />

      {po.statusReason && <p className="mb-4 rounded-md bg-warning-soft px-3 py-2 text-sm text-warning">Note: {po.statusReason}</p>}
      {po.status === "SUBMITTED" && !a.approve && po.createdBy.id === me.user.id && (
        <p className="mb-4 rounded-md bg-info-soft px-3 py-2 text-sm text-info">Waiting for approval by finance or the project manager. You can&apos;t approve your own order.</p>
      )}

      <div className="grid gap-6 lg:grid-cols-3">
        <Card title="Order lines" className="lg:col-span-2">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[600px] text-sm">
              <thead>
                <tr className="border-b border-border bg-surface-muted text-left text-xs uppercase tracking-wide text-muted">
                  <th scope="col" className="px-4 py-2">Material</th>
                  <th scope="col" className="px-4 py-2 text-right">Ordered</th>
                  <th scope="col" className="px-4 py-2 text-right">Received</th>
                  <th scope="col" className="px-4 py-2 text-right">Unit price</th>
                  <th scope="col" className="px-4 py-2 text-right">Line total</th>
                </tr>
              </thead>
              <tbody>
                {po.items.map((i) => (
                  <tr key={i.id} className="border-b border-border last:border-0">
                    <th scope="row" className="px-4 py-2.5 text-left font-medium">{i.material.name}<span className="block text-xs font-normal text-muted">{i.material.code}</span></th>
                    <td className="px-4 py-2.5 text-right tabular-nums">{formatNumber(i.quantityOrdered)} {i.material.unit}</td>
                    <td className="px-4 py-2.5 text-right tabular-nums">{formatNumber(i.quantityReceived)}</td>
                    <td className="px-4 py-2.5 text-right tabular-nums">{formatMoney(i.unitPrice, currency)}</td>
                    <td className="px-4 py-2.5 text-right tabular-nums">{formatMoney(i.lineTotal, currency)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr><td colSpan={4} className="px-4 py-3 text-right font-medium">Total</td><td className="px-4 py-3 text-right font-semibold tabular-nums">{formatMoney(po.totalAmount, currency)}</td></tr>
              </tfoot>
            </table>
          </div>
        </Card>
        <Card title="Details" bodyClassName="space-y-3 p-4 text-sm">
          <p><span className="text-muted">Expected delivery:</span> {formatDate(po.expectedDate)}</p>
          <p><span className="text-muted">Approved by:</span> {po.approvedBy ? `${po.approvedBy.fullName}, ${formatDateTime(po.approvedAt)}` : "-"}</p>
          {po.notes && <p className="whitespace-pre-line text-muted">{po.notes}</p>}
        </Card>
      </div>

      <Card title="Deliveries received" className="mt-6" bodyClassName="p-4">
        {receipts.data?.length ? (
          <ul className="space-y-3 text-sm">
            {receipts.data.map((r) => (
              <li key={r.id} className="rounded-md border border-border p-3">
                <p className="font-medium">Delivery note {r.deliveryNoteRef} <span className="font-normal text-muted">· {r.receivedBy.fullName}, {formatDateTime(r.receivedAt)}</span></p>
                <ul className="mt-1 text-muted">{r.items.map((i) => <li key={i.purchaseOrderItemId}>{i.materialCode} - {formatNumber(i.quantity)}</li>)}</ul>
              </li>
            ))}
          </ul>
        ) : <p className="text-sm text-muted">Nothing received yet.</p>}
      </Card>

      <Dialog open={Boolean(action)} onClose={() => setAction(null)} size="sm"
        title={action === "return" ? "Return for changes" : action === "close" ? "Close order" : "Cancel order"}
        description={action === "close" ? "Use when the supplier can't deliver the rest. Received stock stays." : undefined}
        footer={<><Button variant="outline" onClick={() => setAction(null)}>Back</Button>
          <Button variant={action === "cancel" ? "danger" : "primary"} disabled={!reason.trim()} loading={change.isPending}
            onClick={() => change.mutate({ path: action, body: { version: po.version, reason } })}>Confirm</Button></>}>
        <FormField label="Reason" required>{(f) => <Textarea {...f} value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} />}</FormField>
      </Dialog>

      <Dialog open={editing} onClose={() => setEditing(false)} title={`Edit ${po.number}`} size="lg">
        {editing && <PurchaseOrderForm order={po} submitLabel="Save changes" onCancel={() => setEditing(false)}
          onSubmit={async (body) => {
            try {
              await api.put(`/purchase-orders/${po.id}`, body);
              await queryClient.invalidateQueries({ queryKey: ["purchase-orders"] });
              toast.success("Draft updated");
              setEditing(false);
            } catch (error) { toast.error(errorMessage(error)); throw error; }
          }} />}
      </Dialog>

      {receiving && <ReceiveDialog po={po} onClose={() => setReceiving(false)} />}
    </>
  );
}

/**
 * Books a delivery. The Idempotency-Key is generated once when the dialog opens, so a double click or a
 * network retry sends the same key and the server returns the original receipt instead of adding stock twice.
 */
function ReceiveDialog({ po, onClose }) {
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const [deliveryNoteRef, setDeliveryNoteRef] = useState("");
  const [notes, setNotes] = useState("");
  const [quantities, setQuantities] = useState({});
  const lines = po.items.filter((i) => Number(i.remaining) > 0);

  const receive = useApiMutation(() => api.post(`/purchase-orders/${po.id}/receipts`, {
    deliveryNoteRef, notes: notes || null,
    items: Object.entries(quantities).filter(([, q]) => q && Number(q) > 0).map(([purchaseOrderItemId, quantity]) => ({ purchaseOrderItemId, quantity })),
  }, { headers: { "Idempotency-Key": idempotencyKey } }), { invalidate: KEYS, success: "Delivery received and stock updated", onSuccess: onClose });

  const invalid = lines.some((i) => {
    const q = quantities[i.id];
    return q && (!/^\d{1,11}(\.\d{1,3})?$/.test(q) || Number(q) > Number(i.remaining));
  });
  const any = Object.values(quantities).some((q) => q && Number(q) > 0);

  return (
    <Dialog open onClose={onClose} title={`Receive delivery · ${po.number}`} size="lg"
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button>
        <Button disabled={!deliveryNoteRef.trim() || !any || invalid} loading={receive.isPending} onClick={() => receive.mutate()}>Book delivery</Button></>}>
      <div className="space-y-4">
        <FormField label="Delivery note number" required hint="Each delivery note can be booked once per order.">
          {(f) => <Input {...f} value={deliveryNoteRef} maxLength={80} onChange={(e) => setDeliveryNoteRef(e.target.value)} />}
        </FormField>
        <table className="w-full text-sm">
          <thead><tr className="text-left text-xs uppercase tracking-wide text-muted"><th className="py-1">Material</th><th className="py-1 text-right">Outstanding</th><th className="py-1 text-right">Received now</th></tr></thead>
          <tbody>
            {lines.map((i) => {
              const q = quantities[i.id] ?? "";
              const tooMuch = q && Number(q) > Number(i.remaining);
              return (
                <tr key={i.id} className="border-t border-border">
                  <td className="py-2">{i.material.code} <span className="text-muted">{i.material.name}</span></td>
                  <td className="py-2 text-right tabular-nums">{formatNumber(i.remaining)} {i.material.unit}</td>
                  <td className="py-2 pl-3 text-right">
                    <Input className="ml-auto w-32 text-right" inputMode="decimal" aria-label={`Quantity received for ${i.material.code}`} aria-invalid={tooMuch || undefined}
                      value={q} onChange={(e) => setQuantities((prev) => ({ ...prev, [i.id]: e.target.value.trim() }))} />
                    {tooMuch && <p className="mt-1 text-xs text-danger">More than outstanding</p>}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        <FormField label="Notes">{(f) => <Textarea {...f} value={notes} maxLength={1000} onChange={(e) => setNotes(e.target.value)} />}</FormField>
      </div>
    </Dialog>
  );
}
