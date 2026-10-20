"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/Button";
import { SelectField, TextField } from "@/components/forms/Fields";
import { api, applyServerErrors } from "@/lib/api/client";
import { todayIso } from "@/lib/utils/format";

/** Amounts stay strings end to end: the API receives exactly what the user typed (ADR-0005). */
export const expenseSchema = z.object({
  categoryId: z.string().min(1, "Choose a category"),
  description: z.string().trim().min(1, "Required").max(500),
  vendorName: z.string().max(160).optional(),
  amount: z.string().trim()
    .regex(/^\d{1,13}(\.\d{1,2})?$/, "Enter an amount with up to 2 decimals")
    .refine((v) => Number(v) > 0, "Must be greater than zero"),
  expenseDate: z.string().min(1, "Required").refine((v) => v <= todayIso(), "Cannot be in the future"),
});

export function useBudgetCategories() {
  return useQuery({ queryKey: ["budget-categories"], queryFn: () => api.get("/budget-categories"), staleTime: 300_000 });
}

export function ExpenseForm({ expense, onSubmit, onCancel, submitLabel = "Save draft" }) {
  const { data: categories = [] } = useBudgetCategories();
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(expenseSchema),
    defaultValues: {
      categoryId: expense?.category?.id ?? "",
      description: expense?.description ?? "",
      vendorName: expense?.vendorName ?? "",
      amount: expense?.amount ?? "",
      expenseDate: expense?.expenseDate ?? todayIso(),
    },
  });
  return (
    <form className="grid gap-4 sm:grid-cols-2" noValidate onSubmit={handleSubmit(async (values) => {
      try { await onSubmit(values); } catch (error) { applyServerErrors(error, setError); }
    })}>
      <TextField label="Description" register={register("description")} error={errors.description?.message} required className="sm:col-span-2" />
      <SelectField label="Category" options={categories.filter((c) => c.active).map((c) => ({ value: c.id, label: c.name }))}
        placeholder="Choose…" register={register("categoryId")} error={errors.categoryId?.message} required />
      <TextField label="Vendor" register={register("vendorName")} error={errors.vendorName?.message} />
      <TextField label="Amount" inputMode="decimal" placeholder="0.00" register={register("amount")} error={errors.amount?.message} required />
      <TextField label="Expense date" type="date" max={todayIso()} register={register("expenseDate")} error={errors.expenseDate?.message} required />
      <div className="flex justify-end gap-2 sm:col-span-2">
        {onCancel && <Button variant="outline" onClick={onCancel}>Cancel</Button>}
        <Button type="submit" loading={isSubmitting}>{submitLabel}</Button>
      </div>
    </form>
  );
}
