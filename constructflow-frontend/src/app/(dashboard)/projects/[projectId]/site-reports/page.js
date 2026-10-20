"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { CloudSun, NotebookPen, Plus, Users } from "lucide-react";
import { Card, EmptyState } from "@/components/feedback/States";
import { Pagination } from "@/components/ui/DataTable";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { TextField, TextareaField } from "@/components/forms/Fields";
import { ErrorState, LoadingSkeleton } from "@/components/feedback/States";
import { useAuth } from "@/features/auth/AuthProvider";
import { useProject } from "@/features/projects/api";
import { api, applyServerErrors } from "@/lib/api/client";
import { useApiMutation, usePagedQuery } from "@/lib/api/hooks";
import { formatDate, formatDateTime, todayIso } from "@/lib/utils/format";

const schema = z.object({
  reportDate: z.string().min(1, "Required").refine((v) => v <= todayIso(), "Cannot be in the future"),
  weather: z.string().max(80).optional(),
  workforceCount: z.coerce.number().int().min(0, "0 or more").max(10000),
  workPerformed: z.string().trim().min(1, "Describe the work done").max(4000),
  issues: z.string().max(4000).optional(),
});

export default function SiteReportsPage() {
  const { projectId } = useParams();
  const { can, me } = useAuth();
  const { data: project } = useProject(projectId);
  const [open, setOpen] = useState(false);
  const list = usePagedQuery(["site-reports", projectId], `/projects/${projectId}/site-reports`, { size: 10, initialSort: "reportDate,desc" });
  const canCreate = can("SITE_REPORT_CREATE") && (project?.myProjectRole || me.role === "ADMIN")
    && !["COMPLETED", "CANCELLED"].includes(project?.status);

  return (
    <Card
      title="Daily site diary"
      description="One report per person per day. Reports cannot be edited once submitted."
      actions={canCreate && <Button size="sm" onClick={() => setOpen(true)}><Plus className="h-4 w-4" aria-hidden="true" /> New report</Button>}
    >
      {list.error ? <ErrorState error={list.error} onRetry={list.refetch} /> : list.isPending ? <LoadingSkeleton /> : list.rows.length === 0 ? (
        <EmptyState icon={NotebookPen} title="No site reports yet" description="Site engineers record weather, workforce and progress here each day." />
      ) : (
        <ul className="divide-y divide-border">
          {list.rows.map((r) => (
            <li key={r.id} className="px-4 py-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-medium">{formatDate(r.reportDate)}</p>
                <p className="text-xs text-muted">{r.author.fullName} · submitted {formatDateTime(r.createdAt)}</p>
              </div>
              <div className="mt-1 flex flex-wrap gap-4 text-xs text-muted">
                <span className="inline-flex items-center gap-1"><Users className="h-3.5 w-3.5" aria-hidden="true" />{r.workforceCount} on site</span>
                {r.weather && <span className="inline-flex items-center gap-1"><CloudSun className="h-3.5 w-3.5" aria-hidden="true" />{r.weather}</span>}
              </div>
              <p className="mt-2 whitespace-pre-line text-sm">{r.workPerformed}</p>
              {r.issues && <p className="mt-2 rounded-md bg-warning-soft px-3 py-2 text-sm text-warning">Issues: {r.issues}</p>}
            </li>
          ))}
        </ul>
      )}
      <Pagination page={list.page} size={list.size} totalPages={list.data?.totalPages} totalElements={list.data?.totalElements} onPageChange={list.setPage} />
      <Dialog open={open} onClose={() => setOpen(false)} title="New site report">
        {open && <SiteReportForm projectId={projectId} onDone={() => setOpen(false)} />}
      </Dialog>
    </Card>
  );
}

function SiteReportForm({ projectId, onDone }) {
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { reportDate: todayIso(), weather: "", workforceCount: 0, workPerformed: "", issues: "" },
  });
  const create = useApiMutation((body) => api.post(`/projects/${projectId}/site-reports`, body), {
    invalidate: [["site-reports"]], success: "Site report submitted", onSuccess: onDone,
  });
  return (
    <form className="grid gap-4 sm:grid-cols-2" noValidate onSubmit={handleSubmit(async (values) => {
      try { await create.mutateAsync(values); } catch (error) { applyServerErrors(error, setError); }
    })}>
      <TextField label="Date" type="date" register={register("reportDate")} error={errors.reportDate?.message} required />
      <TextField label="Workforce on site" type="number" min={0} register={register("workforceCount")} error={errors.workforceCount?.message} required />
      <TextField label="Weather" placeholder="Sunny, 29°C" register={register("weather")} error={errors.weather?.message} className="sm:col-span-2" />
      <TextareaField label="Work performed" rows={4} register={register("workPerformed")} error={errors.workPerformed?.message} required className="sm:col-span-2" />
      <TextareaField label="Issues or delays" register={register("issues")} error={errors.issues?.message} className="sm:col-span-2" />
      <div className="flex justify-end gap-2 sm:col-span-2">
        <Button variant="outline" onClick={onDone}>Cancel</Button>
        <Button type="submit" loading={isSubmitting}>Submit report</Button>
      </div>
    </form>
  );
}
