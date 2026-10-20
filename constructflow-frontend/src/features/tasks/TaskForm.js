"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/Button";
import { SelectField, TextField, TextareaField } from "@/components/forms/Fields";
import { api, applyServerErrors } from "@/lib/api/client";
import { TASK_PRIORITIES } from "@/lib/constants/status";
import { useProjectMembers } from "@/features/projects/api";

export const taskSchema = z
  .object({
    title: z.string().trim().min(1, "Required").max(200),
    description: z.string().max(4000).optional(),
    assigneeId: z.string().optional(),
    milestoneId: z.string().optional(),
    priority: z.enum(TASK_PRIORITIES),
    weight: z.coerce.number().int().min(1, "1–10").max(10, "1–10"),
    startDate: z.string().optional(),
    dueDate: z.string().optional(),
  })
  .refine((v) => !v.startDate || !v.dueDate || v.dueDate >= v.startDate, { path: ["dueDate"], message: "Must be on or after the start date" });

export function TaskForm({ projectId, task, onSubmit, onCancel }) {
  const { data: members = [] } = useProjectMembers(projectId);
  const { data: milestones = [] } = useQuery({
    queryKey: ["milestones", projectId],
    queryFn: () => api.get(`/projects/${projectId}/milestones`),
  });
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(taskSchema),
    defaultValues: {
      title: task?.title ?? "",
      description: task?.description ?? "",
      assigneeId: task?.assignee?.id ?? "",
      milestoneId: task?.milestone?.id ?? "",
      priority: task?.priority ?? "MEDIUM",
      weight: task?.weight ?? 1,
      startDate: task?.startDate ?? "",
      dueDate: task?.dueDate ?? "",
    },
  });

  const submit = async (values) => {
    const body = {
      ...values,
      assigneeId: values.assigneeId || null,
      milestoneId: values.milestoneId || null,
      startDate: values.startDate || null,
      dueDate: values.dueDate || null,
      version: task?.version,
    };
    try {
      await onSubmit(body);
    } catch (error) {
      applyServerErrors(error, setError);
    }
  };

  return (
    <form onSubmit={handleSubmit(submit)} className="grid gap-4 sm:grid-cols-2" noValidate>
      <TextField label="Title" register={register("title")} error={errors.title?.message} required className="sm:col-span-2" />
      <SelectField
        label="Assignee"
        options={members.map((m) => ({ value: m.userId, label: m.fullName }))}
        placeholder="Unassigned"
        register={register("assigneeId")}
        error={errors.assigneeId?.message}
        hint="Only project members can be assigned."
      />
      <SelectField label="Milestone" options={milestones.map((m) => ({ value: m.id, label: m.name }))} placeholder="None" register={register("milestoneId")} error={errors.milestoneId?.message} />
      <SelectField label="Priority" options={TASK_PRIORITIES} register={register("priority")} error={errors.priority?.message} required />
      <TextField label="Weight" type="number" min={1} max={10} hint="1–10, how much this task counts toward progress" register={register("weight")} error={errors.weight?.message} required />
      <TextField label="Start date" type="date" register={register("startDate")} error={errors.startDate?.message} />
      <TextField label="Due date" type="date" register={register("dueDate")} error={errors.dueDate?.message} />
      <TextareaField label="Description" register={register("description")} error={errors.description?.message} className="sm:col-span-2" />
      <div className="flex justify-end gap-2 sm:col-span-2">
        {onCancel && <Button variant="outline" onClick={onCancel}>Cancel</Button>}
        <Button type="submit" loading={isSubmitting}>{task ? "Save task" : "Create task"}</Button>
      </div>
    </form>
  );
}
