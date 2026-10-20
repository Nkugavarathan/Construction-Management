"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/Button";
import { SelectField, TextField, TextareaField } from "@/components/forms/Fields";
import { applyServerErrors } from "@/lib/api/client";
import { useOrganizationMembers } from "./api";

const money = z.string().trim().regex(/^\d{1,13}(\.\d{1,2})?$/, "Enter an amount with up to 2 decimals");

/** Client-side checks for fast feedback; the API re-validates everything and is authoritative. */
export const projectSchema = z
  .object({
    code: z.string().trim().regex(/^[A-Za-z0-9-]{2,32}$/, "2–32 letters, digits or dashes"),
    name: z.string().trim().min(1, "Required").max(160),
    description: z.string().max(4000).optional(),
    clientName: z.string().trim().min(1, "Required").max(160),
    clientContact: z.string().max(160).optional(),
    siteAddress: z.string().trim().min(1, "Required").max(300),
    startDate: z.string().min(1, "Required"),
    plannedEndDate: z.string().min(1, "Required"),
    approvedBudget: money,
    managerId: z.string().optional(),
  })
  .refine((v) => !v.startDate || !v.plannedEndDate || v.plannedEndDate >= v.startDate, {
    path: ["plannedEndDate"],
    message: "Must be on or after the start date",
  });

export function ProjectForm({ project, onSubmit, submitLabel = "Save", onCancel }) {
  const { data: members = [] } = useOrganizationMembers();
  const managers = members
    .filter((m) => m.role === "PROJECT_MANAGER" || m.role === "ADMIN")
    .map((m) => ({ value: m.user.id, label: `${m.user.fullName} (${m.role === "ADMIN" ? "Admin" : "PM"})` }));

  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(project ? projectSchema.omit({ code: true }) : projectSchema),
    defaultValues: {
      code: project?.code ?? "",
      name: project?.name ?? "",
      description: project?.description ?? "",
      clientName: project?.clientName ?? "",
      clientContact: project?.clientContact ?? "",
      siteAddress: project?.siteAddress ?? "",
      startDate: project?.startDate ?? "",
      plannedEndDate: project?.plannedEndDate ?? "",
      approvedBudget: project?.approvedBudget ?? "",
      managerId: project?.manager?.id ?? "",
    },
  });

  const submit = async (values) => {
    try {
      await onSubmit({ ...values, managerId: values.managerId || null });
    } catch (error) {
      applyServerErrors(error, setError);
    }
  };

  return (
    <form onSubmit={handleSubmit(submit)} className="grid gap-4 sm:grid-cols-2" noValidate>
      {!project && <TextField label="Project code" placeholder="KDY-002" register={register("code")} error={errors.code?.message} required />}
      <TextField label="Project name" register={register("name")} error={errors.name?.message} required className={project ? "sm:col-span-2" : ""} />
      <TextField label="Client" register={register("clientName")} error={errors.clientName?.message} required />
      <TextField label="Client contact" register={register("clientContact")} error={errors.clientContact?.message} />
      <TextField label="Site address" register={register("siteAddress")} error={errors.siteAddress?.message} required className="sm:col-span-2" />
      <TextField label="Start date" type="date" register={register("startDate")} error={errors.startDate?.message} required />
      <TextField label="Planned completion" type="date" register={register("plannedEndDate")} error={errors.plannedEndDate?.message} required />
      <TextField label="Approved budget" inputMode="decimal" register={register("approvedBudget")} error={errors.approvedBudget?.message} required />
      <SelectField
        label="Project manager"
        options={managers}
        placeholder={project ? "Keep current" : "Me"}
        register={register("managerId")}
        error={errors.managerId?.message}
      />
      <TextareaField label="Description" rows={3} register={register("description")} error={errors.description?.message} className="sm:col-span-2" />
      <div className="flex justify-end gap-2 sm:col-span-2">
        {onCancel && <Button variant="outline" onClick={onCancel}>Cancel</Button>}
        <Button type="submit" loading={isSubmitting}>{submitLabel}</Button>
      </div>
    </form>
  );
}
