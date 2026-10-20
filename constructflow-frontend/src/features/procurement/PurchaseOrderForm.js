"use client";

import { useFieldArray, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQuery } from "@tanstack/react-query";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Input, Select, SelectField, TextField, TextareaField } from "@/components/forms/Fields";
import { useAuth } from "@/features/auth/AuthProvider";
import { api, applyServerErrors } from "@/lib/api/client";
import { formatMoney } from "@/lib/utils/format";

const line = z.object({
  materialId: z.string().min(1, "Choose a material"),
  quantity: z.string().trim().regex(/^\d{1,11}(\.\d{1,3})?$/, "Up to 3 decimals").refine((v) => Number(v) > 0, "Must be > 0"),
  unitPrice: z.string().trim().regex(/^\d{1,11}(\.\d{1,4})?$/, "Up to 4 decimals"),
});

export const purchaseOrderSchema = z.object({
  supplierId: z.string().min(1, "Choose a supplier"),
  projectId: z.string().optional(),
  expectedDate: z.string().optional(),
  notes: z.string().max(1000).optional(),
  items: z.array(line).min(1, "Add at least one line")
    .refine((items) => new Set(items.map((i) => i.materialId)).size === items.length, "Each material may appear only once"),
});

export function PurchaseOrderForm({ order, onSubmit, onCancel, submitLabel = "Save draft" }) {
  const { me } = useAuth();
  const currency = me.activeOrganization.currencyCode;
  const suppliers = useQuery({ queryKey: ["suppliers", "options"], queryFn: () => api.get("/suppliers", { size: 100, sort: "name,asc" }).then((p) => p.content) });
  const materials = useQuery({ queryKey: ["materials", "options"], queryFn: () => api.get("/materials", { size: 100, sort: "code,asc" }).then((p) => p.content) });
  const projects = useQuery({ queryKey: ["projects", "open-options"], queryFn: () => api.get("/projects", { size: 100, sort: "name,asc" }).then((p) => p.content.filter((x) => !["COMPLETED", "CANCELLED"].includes(x.status))) });

  const { register, control, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(purchaseOrderSchema),
    defaultValues: {
      supplierId: order?.supplier?.id ?? "",
      projectId: order?.project?.id ?? "",
      expectedDate: order?.expectedDate ?? "",
      notes: order?.notes ?? "",
      items: order?.items?.map((i) => ({ materialId: i.material.id, quantity: String(i.quantityOrdered), unitPrice: String(i.unitPrice) })) ?? [{ materialId: "", quantity: "", unitPrice: "" }],
    },
  });
  const { fields, append, remove } = useFieldArray({ control, name: "items" });
  const items = useWatch({ control, name: "items" });
  // Indicative total for the person typing; the server computes the stored total with exact decimals.
  const estimate = (items ?? []).reduce((sum, i) => sum + (Number(i.quantity) || 0) * (Number(i.unitPrice) || 0), 0);

  return (
    <form className="space-y-6" noValidate onSubmit={handleSubmit(async (v) => {
      try {
        await onSubmit({ ...v, projectId: v.projectId || null, expectedDate: v.expectedDate || null, version: order?.version });
      } catch (e) { applyServerErrors(e, setError); }
    })}>
      <div className="grid gap-4 sm:grid-cols-3">
        <SelectField label="Supplier" required options={(suppliers.data ?? []).filter((s) => s.active).map((s) => ({ value: s.id, label: s.name }))}
          placeholder="Choose…" register={register("supplierId")} error={errors.supplierId?.message} />
        <SelectField label="For project" options={(projects.data ?? []).map((p) => ({ value: p.id, label: `${p.code} - ${p.name}` }))}
          placeholder={me.role === "PROJECT_MANAGER" ? "Choose a project" : "General stock"} register={register("projectId")} error={errors.projectId?.message}
          hint={me.role === "PROJECT_MANAGER" ? "Project managers order for projects they manage." : undefined} />
        <TextField label="Expected delivery" type="date" register={register("expectedDate")} error={errors.expectedDate?.message} />
      </div>

      <fieldset>
        <legend className="mb-2 text-sm font-medium">Order lines</legend>
        {errors.items?.root?.message || errors.items?.message ? <p role="alert" className="mb-2 text-xs text-danger">{errors.items?.root?.message ?? errors.items?.message}</p> : null}
        <div className="space-y-2">
          {fields.map((field, index) => (
            <div key={field.id} className="grid gap-2 sm:grid-cols-[1fr_140px_160px_auto] sm:items-start">
              <div>
                <Select aria-label={`Material, line ${index + 1}`} {...register(`items.${index}.materialId`)} placeholder="Material…"
                  options={(materials.data ?? []).filter((m) => m.active).map((m) => ({ value: m.id, label: `${m.code} - ${m.name} (${m.unit})` }))}
                  aria-invalid={errors.items?.[index]?.materialId ? true : undefined} />
                {errors.items?.[index]?.materialId && <p className="mt-1 text-xs text-danger">{errors.items[index].materialId.message}</p>}
              </div>
              <div>
                <Input aria-label={`Quantity, line ${index + 1}`} inputMode="decimal" placeholder="Qty" {...register(`items.${index}.quantity`)} aria-invalid={errors.items?.[index]?.quantity ? true : undefined} />
                {errors.items?.[index]?.quantity && <p className="mt-1 text-xs text-danger">{errors.items[index].quantity.message}</p>}
              </div>
              <div>
                <Input aria-label={`Unit price, line ${index + 1}`} inputMode="decimal" placeholder="Unit price" {...register(`items.${index}.unitPrice`)} aria-invalid={errors.items?.[index]?.unitPrice ? true : undefined} />
                {errors.items?.[index]?.unitPrice && <p className="mt-1 text-xs text-danger">{errors.items[index].unitPrice.message}</p>}
              </div>
              <Button variant="ghost" size="icon" onClick={() => remove(index)} disabled={fields.length === 1} aria-label={`Remove line ${index + 1}`}>
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          ))}
        </div>
        <div className="mt-3 flex items-center justify-between">
          <Button variant="outline" size="sm" onClick={() => append({ materialId: "", quantity: "", unitPrice: "" })}><Plus className="h-4 w-4" aria-hidden="true" /> Add line</Button>
          <p className="text-sm text-muted">Estimated total <span className="font-semibold text-text">{formatMoney(estimate, currency)}</span></p>
        </div>
      </fieldset>

      <TextareaField label="Notes for the supplier" register={register("notes")} error={errors.notes?.message} />
      <div className="flex justify-end gap-2">
        {onCancel && <Button variant="outline" onClick={onCancel}>Cancel</Button>}
        <Button type="submit" loading={isSubmitting}>{submitLabel}</Button>
      </div>
    </form>
  );
}
