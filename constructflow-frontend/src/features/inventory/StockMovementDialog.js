"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { FormField, Input, Select, Textarea } from "@/components/forms/Fields";
import { api } from "@/lib/api/client";
import { useApiMutation } from "@/lib/api/hooks";
import { formatNumber } from "@/lib/utils/format";

const QUANTITY = /^\d{1,11}(\.\d{1,3})?$/;

const CONFIG = {
  receipt: { title: "Receive stock", path: "/inventory/receipts", label: "Quantity received", success: "Stock received" },
  issue: { title: "Issue to project", path: "/inventory/issues", label: "Quantity to issue", success: "Stock issued" },
  adjustment: { title: "Adjust stock", path: "/inventory/adjustments", label: "Adjustment (+ or −)", success: "Stock adjusted" },
};

/**
 * One dialog for the three manual stock movements. The server locks the material row and refuses to
 * go below zero, so the "available" figure here is guidance, not the check.
 */
export function StockMovementDialog({ kind, material, onClose }) {
  const cfg = CONFIG[kind];
  const [quantity, setQuantity] = useState("");
  const [projectId, setProjectId] = useState("");
  const [reason, setReason] = useState("");
  const projects = useQuery({
    queryKey: ["projects", "active-options"],
    queryFn: () => api.get("/projects", { status: "ACTIVE", size: 100, sort: "name,asc" }).then((p) => p.content),
    enabled: kind === "issue",
  });

  const mutation = useApiMutation(() => {
    if (kind === "receipt") return api.post(cfg.path, { materialId: material.id, quantity, reason });
    if (kind === "issue") return api.post(cfg.path, { materialId: material.id, projectId, quantity, note: reason || null });
    return api.post(cfg.path, { materialId: material.id, quantityDelta: quantity, reason });
  }, { invalidate: [["materials"], ["movements"], ["dashboard"]], success: cfg.success, onSuccess: onClose });

  const magnitude = quantity.replace(/^[-+]/, "");
  const validQuantity = QUANTITY.test(magnitude) && Number(magnitude) > 0 && (kind === "adjustment" || !quantity.startsWith("-"));
  const exceeds = kind === "issue" && validQuantity && Number(quantity) > Number(material.quantityOnHand);
  const valid = validQuantity && !exceeds && (kind === "issue" ? Boolean(projectId) : reason.trim().length > 0);

  return (
    <Dialog open onClose={onClose} title={`${cfg.title} · ${material.code}`}
      description={`${material.name} — ${formatNumber(material.quantityOnHand)} ${material.unit} on hand`}
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button disabled={!valid} loading={mutation.isPending} onClick={() => mutation.mutate()}>Confirm</Button></>}>
      <div className="space-y-4">
        {kind === "issue" && (
          <FormField label="Project" required hint="Materials can only be issued to active projects.">
            {(a) => <Select {...a} value={projectId} onChange={(e) => setProjectId(e.target.value)} placeholder="Choose a project"
              options={(projects.data ?? []).map((p) => ({ value: p.id, label: `${p.code} — ${p.name}` }))} />}
          </FormField>
        )}
        <FormField label={`${cfg.label} (${material.unit})`} required
          error={quantity && !validQuantity ? "Enter a positive number with up to 3 decimals" : exceeds ? "More than the stock on hand" : undefined}>
          {(a) => <Input {...a} inputMode="decimal" value={quantity} onChange={(e) => setQuantity(e.target.value.trim())} placeholder={kind === "adjustment" ? "-5 or 5" : "0"} />}
        </FormField>
        <FormField label={kind === "issue" ? "Note" : "Reason"} required={kind !== "issue"} hint="Recorded in the stock ledger and audit log.">
          {(a) => <Textarea {...a} value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} />}
        </FormField>
      </div>
    </Dialog>
  );
}
