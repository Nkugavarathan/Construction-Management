"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Plus, ShieldAlert } from "lucide-react";
import { Card, EmptyState } from "@/components/feedback/States";
import { DataTable, Pagination } from "@/components/ui/DataTable";
import { FilterBar } from "@/components/ui/FilterBar";
import { StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { Select, SelectField, TextField, TextareaField } from "@/components/forms/Fields";
import { useProject, useProjectMembers } from "@/features/projects/api";
import { api, applyServerErrors } from "@/lib/api/client";
import { useApiMutation, usePagedQuery } from "@/lib/api/hooks";
import { formatDate } from "@/lib/utils/format";

const STATUSES = ["OPEN", "MITIGATING", "CLOSED"];
const SCALE = [1, 2, 3, 4, 5].map((n) => ({ value: String(n), label: String(n) }));

/** Same banding as the server (Risk.Level): shown live as a preview; the API computes the stored score. */
function levelOf(score) {
  if (score >= 17) return "CRITICAL";
  if (score >= 10) return "HIGH";
  if (score >= 5) return "MEDIUM";
  return "LOW";
}

const schema = z.object({
  title: z.string().trim().min(1, "Required").max(200),
  description: z.string().max(4000).optional(),
  likelihood: z.coerce.number().int().min(1).max(5),
  impact: z.coerce.number().int().min(1).max(5),
  mitigation: z.string().max(4000).optional(),
  ownerId: z.string().optional(),
  dueDate: z.string().optional(),
  status: z.enum(STATUSES).optional(),
});

export default function RisksPage() {
  const { projectId } = useParams();
  const { data: project } = useProject(projectId);
  const [status, setStatus] = useState("");
  const [editing, setEditing] = useState(null);
  const list = usePagedQuery(["risks", projectId], `/projects/${projectId}/risks`, { filters: { status }, initialSort: "score,desc" });
  const canManage = project?.canManage && !["COMPLETED", "CANCELLED"].includes(project?.status);

  const columns = [
    { header: "Risk", cell: ({ row }) => (<div><p className="font-medium">{row.original.title}</p>{row.original.mitigation && <p className="line-clamp-1 text-xs text-muted">{row.original.mitigation}</p>}</div>) },
    { header: "L × I", cell: ({ row }) => <span className="tabular-nums">{row.original.likelihood} × {row.original.impact}</span> },
    { header: "Score", meta: { sortField: "score" }, cell: ({ row }) => (<div className="flex items-center gap-2"><span className="font-semibold tabular-nums">{row.original.score}</span><StatusBadge status={row.original.level} /></div>) },
    { header: "Owner", cell: ({ row }) => row.original.owner?.fullName ?? "-" },
    { header: "Due", meta: { sortField: "dueDate" }, cell: ({ row }) => formatDate(row.original.dueDate) },
    { header: "Status", meta: { sortField: "status" }, cell: ({ row }) => <StatusBadge status={row.original.status} /> },
  ];

  return (
    <Card title="Risk register" description="Score = likelihood × impact (1–25): Low 1–4, Medium 5–9, High 10–16, Critical 17–25."
      actions={canManage && <Button size="sm" onClick={() => setEditing({})}><Plus className="h-4 w-4" aria-hidden="true" /> Add risk</Button>}>
      <FilterBar>
        <Select aria-label="Status" className="w-full sm:w-44" value={status} onChange={(e) => { setStatus(e.target.value); list.setPage(0); }} options={STATUSES} placeholder="All statuses" />
      </FilterBar>
      <DataTable columns={columns} data={list.rows} loading={list.isPending} error={list.error} onRetry={list.refetch}
        sort={list.sort} onSortChange={list.setSort} onRowClick={canManage ? (row) => setEditing(row) : undefined}
        empty={<EmptyState icon={ShieldAlert} title="No risks recorded" />} />
      <Pagination page={list.page} size={list.size} totalPages={list.data?.totalPages} totalElements={list.data?.totalElements} onPageChange={list.setPage} />
      <Dialog open={Boolean(editing)} onClose={() => setEditing(null)} title={editing?.id ? "Edit risk" : "Add risk"} size="lg">
        {editing && <RiskForm key={editing.id ?? "new"} projectId={projectId} risk={editing.id ? editing : null} onDone={() => setEditing(null)} />}
      </Dialog>
    </Card>
  );
}

function RiskForm({ projectId, risk, onDone }) {
  const { data: members = [] } = useProjectMembers(projectId);
  const { register, handleSubmit, setError, control, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(schema),
    defaultValues: {
      title: risk?.title ?? "", description: risk?.description ?? "", likelihood: String(risk?.likelihood ?? 3),
      impact: String(risk?.impact ?? 3), mitigation: risk?.mitigation ?? "", ownerId: risk?.owner?.id ?? "",
      dueDate: risk?.dueDate ?? "", status: risk?.status ?? "OPEN",
    },
  });
  const [likelihood, impact] = useWatch({ control, name: ["likelihood", "impact"] });
  const score = Number(likelihood) * Number(impact);
  const save = useApiMutation(
    (body) => (risk ? api.patch(`/risks/${risk.id}`, { ...body, version: risk.version }) : api.post(`/projects/${projectId}/risks`, body)),
    { invalidate: [["risks"], ["dashboard"]], success: risk ? "Risk updated" : "Risk added", onSuccess: onDone },
  );
  return (
    <form className="grid gap-4 sm:grid-cols-2" noValidate onSubmit={handleSubmit(async (v) => {
      try { await save.mutateAsync({ ...v, ownerId: v.ownerId || null, dueDate: v.dueDate || null }); } catch (e) { applyServerErrors(e, setError); }
    })}>
      <TextField label="Title" register={register("title")} error={errors.title?.message} required className="sm:col-span-2" />
      <SelectField label="Likelihood (1–5)" options={SCALE} register={register("likelihood")} error={errors.likelihood?.message} required />
      <SelectField label="Impact (1–5)" options={SCALE} register={register("impact")} error={errors.impact?.message} required />
      <p className="flex items-center gap-2 text-sm sm:col-span-2" aria-live="polite">
        Score <span className="font-semibold tabular-nums">{score}</span> <StatusBadge status={levelOf(score)} />
      </p>
      <SelectField label="Owner" options={members.map((m) => ({ value: m.userId, label: m.fullName }))} placeholder="Unassigned" register={register("ownerId")} error={errors.ownerId?.message} />
      <TextField label="Review by" type="date" register={register("dueDate")} error={errors.dueDate?.message} />
      {risk && <SelectField label="Status" options={STATUSES} register={register("status")} error={errors.status?.message} />}
      <TextareaField label="Description" register={register("description")} error={errors.description?.message} className="sm:col-span-2" />
      <TextareaField label="Mitigation" register={register("mitigation")} error={errors.mitigation?.message} className="sm:col-span-2" />
      <div className="flex justify-end gap-2 sm:col-span-2">
        <Button variant="outline" onClick={onDone}>Cancel</Button>
        <Button type="submit" loading={isSubmitting}>Save risk</Button>
      </div>
    </form>
  );
}
