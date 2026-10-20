"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Flag, Plus } from "lucide-react";
import { Card, EmptyState, ErrorState, LoadingSkeleton, ProgressBar } from "@/components/feedback/States";
import { StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { TextField, TextareaField } from "@/components/forms/Fields";
import { useProject } from "@/features/projects/api";
import { api, applyServerErrors } from "@/lib/api/client";
import { useApiMutation } from "@/lib/api/hooks";
import { formatDate } from "@/lib/utils/format";

const schema = z.object({
  name: z.string().trim().min(1, "Required").max(160),
  description: z.string().max(2000).optional(),
  dueDate: z.string().min(1, "Required"),
});

export default function MilestonesPage() {
  const { projectId } = useParams();
  const { data: project } = useProject(projectId);
  const [editing, setEditing] = useState(null);
  const { data, error, isPending, refetch } = useQuery({
    queryKey: ["milestones", projectId],
    queryFn: () => api.get(`/projects/${projectId}/milestones`),
  });
  const canManage = project?.canManage && !["COMPLETED", "CANCELLED"].includes(project?.status);
  const toggle = useApiMutation(
    (m) => api.patch(`/milestones/${m.id}`, { name: m.name, description: m.description, dueDate: m.dueDate,
      status: m.status === "COMPLETED" ? "PENDING" : "COMPLETED", version: m.version }),
    { invalidate: [["milestones"], ["dashboard"]], success: "Milestone updated" },
  );

  return (
    <Card
      title="Milestones"
      description="Progress is the weighted share of each milestone's tasks that are done."
      actions={canManage && <Button size="sm" onClick={() => setEditing({})}><Plus className="h-4 w-4" aria-hidden="true" /> Add milestone</Button>}
    >
      {error ? <ErrorState error={error} onRetry={refetch} /> : isPending ? <LoadingSkeleton /> : data.length === 0 ? (
        <EmptyState icon={Flag} title="No milestones yet" description="Milestones mark key dates such as 'Foundations complete'." />
      ) : (
        <ul className="divide-y divide-border">
          {data.map((m) => (
            <li key={m.id} className="grid gap-3 px-4 py-4 md:grid-cols-[1fr_200px_auto] md:items-center">
              <div>
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-medium">{m.name}</p>
                  <StatusBadge status={m.status === "COMPLETED" ? "DONE" : m.overdue ? "OVERDUE" : "PENDING"} />
                </div>
                <p className={`text-xs ${m.overdue ? "text-danger" : "text-muted"}`}>Due {formatDate(m.dueDate)}</p>
                {m.description && <p className="mt-1 text-sm text-muted">{m.description}</p>}
              </div>
              <ProgressBar value={m.progressPercent} label={`${m.name} progress`} />
              {canManage && (
                <div className="flex gap-2">
                  <Button size="sm" variant="outline" onClick={() => setEditing(m)}>Edit</Button>
                  <Button size="sm" variant={m.status === "COMPLETED" ? "ghost" : "secondary"} loading={toggle.isPending && toggle.variables?.id === m.id} onClick={() => toggle.mutate(m)}>
                    {m.status === "COMPLETED" ? "Reopen" : "Complete"}
                  </Button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
      <MilestoneDialog projectId={projectId} milestone={editing} onClose={() => setEditing(null)} />
    </Card>
  );
}

function MilestoneDialog({ projectId, milestone, onClose }) {
  // The dialog is mounted with milestone = null while closed, so never dereference it unconditionally.
  const isNew = Boolean(milestone) && !milestone?.id;
  const id = milestone?.id;
  const status = milestone?.status;
  const version = milestone?.version;
  const save = useApiMutation(
    (values) => (isNew
      ? api.post(`/projects/${projectId}/milestones`, values)
      : api.patch(`/milestones/${id}`, { ...values, status, version })),
    { invalidate: [["milestones"], ["dashboard"]], success: isNew ? "Milestone added" : "Milestone updated", onSuccess: onClose, silentError: false },
  );
  return (
    <Dialog open={Boolean(milestone)} onClose={onClose} title={isNew ? "Add milestone" : "Edit milestone"}>
      {milestone && <MilestoneForm key={milestone.id ?? "new"} milestone={milestone} onSubmit={save.mutateAsync} onCancel={onClose} />}
    </Dialog>
  );
}

function MilestoneForm({ milestone, onSubmit, onCancel }) {
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { name: milestone.name ?? "", description: milestone.description ?? "", dueDate: milestone.dueDate ?? "" },
  });
  return (
    <form className="space-y-4" noValidate onSubmit={handleSubmit(async (values) => {
      try { await onSubmit(values); } catch (error) { applyServerErrors(error, setError); }
    })}>
      <TextField label="Name" register={register("name")} error={errors.name?.message} required />
      <TextField label="Due date" type="date" register={register("dueDate")} error={errors.dueDate?.message} required />
      <TextareaField label="Description" register={register("description")} error={errors.description?.message} />
      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onCancel}>Cancel</Button>
        <Button type="submit" loading={isSubmitting}>Save</Button>
      </div>
    </form>
  );
}
