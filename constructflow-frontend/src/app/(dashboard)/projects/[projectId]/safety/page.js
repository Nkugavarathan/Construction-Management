"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { HardHat, Plus } from "lucide-react";
import { Card, EmptyState } from "@/components/feedback/States";
import { DataTable, Pagination } from "@/components/ui/DataTable";
import { FilterBar } from "@/components/ui/FilterBar";
import { StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { FormField, Input, Select, Textarea } from "@/components/forms/Fields";
import { useAuth } from "@/features/auth/AuthProvider";
import { useProject } from "@/features/projects/api";
import { api } from "@/lib/api/client";
import { useApiMutation, usePagedQuery } from "@/lib/api/hooks";
import { INCIDENT_SEVERITIES } from "@/lib/constants/status";
import { formatDateTime } from "@/lib/utils/format";

const STATUSES = ["OPEN", "INVESTIGATING", "CLOSED"];

function nowLocal() {
  const d = new Date();
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().slice(0, 16);
}

export default function SafetyPage() {
  const { projectId } = useParams();
  const { can } = useAuth();
  const { data: project } = useProject(projectId);
  const [status, setStatus] = useState("");
  const [reporting, setReporting] = useState(false);
  const [selected, setSelected] = useState(null);
  const list = usePagedQuery(["incidents", projectId], `/projects/${projectId}/incidents`, { filters: { status }, initialSort: "occurredAt,desc" });

  const columns = [
    { header: "When", meta: { sortField: "occurredAt" }, cell: ({ row }) => formatDateTime(row.original.occurredAt) },
    { header: "Incident", cell: ({ row }) => (<div><p className="font-medium">{row.original.location}</p><p className="line-clamp-1 text-xs text-muted">{row.original.description}</p></div>) },
    { header: "Severity", cell: ({ row }) => <StatusBadge status={row.original.severity} /> },
    { header: "Reported by", cell: ({ row }) => row.original.reportedBy.fullName },
    { header: "Status", meta: { sortField: "status" }, cell: ({ row }) => <StatusBadge status={row.original.status} /> },
  ];

  return (
    <Card title="Safety incidents" description="Report near misses as well as injuries: they are how accidents get prevented."
      actions={can("INCIDENT_REPORT") && <Button size="sm" onClick={() => setReporting(true)}><Plus className="h-4 w-4" aria-hidden="true" /> Report incident</Button>}>
      <FilterBar>
        <Select aria-label="Status" className="w-full sm:w-44" value={status} onChange={(e) => { setStatus(e.target.value); list.setPage(0); }} options={STATUSES} placeholder="All statuses" />
      </FilterBar>
      <DataTable columns={columns} data={list.rows} loading={list.isPending} error={list.error} onRetry={list.refetch}
        sort={list.sort} onSortChange={list.setSort} onRowClick={(row) => setSelected(row)}
        empty={<EmptyState icon={HardHat} title="No incidents reported" />} />
      <Pagination page={list.page} size={list.size} totalPages={list.data?.totalPages} totalElements={list.data?.totalElements} onPageChange={list.setPage} />
      {reporting && <ReportDialog projectId={projectId} onClose={() => setReporting(false)} />}
      {selected && <IncidentDialog incident={selected} canManage={project?.canManage} onClose={() => setSelected(null)} />}
    </Card>
  );
}

function ReportDialog({ projectId, onClose }) {
  const [form, setForm] = useState({ occurredAt: nowLocal(), location: "", severity: "NEAR_MISS", description: "" });
  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));
  const report = useApiMutation(
    () => api.post(`/projects/${projectId}/incidents`, { ...form, occurredAt: new Date(form.occurredAt).toISOString() }),
    { invalidate: [["incidents"], ["dashboard"]], success: "Incident reported. Project managers have been notified.", onSuccess: onClose },
  );
  const valid = form.location.trim() && form.description.trim() && form.occurredAt && new Date(form.occurredAt) <= new Date();
  return (
    <Dialog open onClose={onClose} title="Report a safety incident"
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button disabled={!valid} loading={report.isPending} onClick={() => report.mutate()}>Submit report</Button></>}>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField label="When did it happen?" required>{(a) => <Input {...a} type="datetime-local" max={nowLocal()} value={form.occurredAt} onChange={set("occurredAt")} />}</FormField>
        <FormField label="Severity" required>{(a) => <Select {...a} value={form.severity} onChange={set("severity")} options={INCIDENT_SEVERITIES} />}</FormField>
        <FormField label="Location on site" required className="sm:col-span-2">{(a) => <Input {...a} value={form.location} maxLength={200} onChange={set("location")} />}</FormField>
        <FormField label="What happened?" required className="sm:col-span-2">{(a) => <Textarea {...a} rows={4} value={form.description} maxLength={4000} onChange={set("description")} />}</FormField>
      </div>
    </Dialog>
  );
}

function IncidentDialog({ incident, canManage, onClose }) {
  const [severity, setSeverity] = useState(incident.severity);
  const [actions, setActions] = useState(incident.correctiveActions ?? "");
  const [status, setStatus] = useState(incident.status);
  const save = useApiMutation(
    () => api.patch(`/incidents/${incident.id}`, { severity, correctiveActions: actions, status, version: incident.version }),
    { invalidate: [["incidents"], ["dashboard"]], success: "Incident updated", onSuccess: onClose },
  );
  return (
    <Dialog open onClose={onClose} title={`Incident at ${incident.location}`} size="lg"
      footer={canManage ? <><Button variant="outline" onClick={onClose}>Close</Button><Button loading={save.isPending} onClick={() => save.mutate()}>Save</Button></> : undefined}>
      <div className="space-y-4 text-sm">
        <p className="text-muted">{formatDateTime(incident.occurredAt)} · reported by {incident.reportedBy.fullName}</p>
        <p className="whitespace-pre-line">{incident.description}</p>
        {canManage ? (
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField label="Severity">{(a) => <Select {...a} value={severity} onChange={(e) => setSeverity(e.target.value)} options={INCIDENT_SEVERITIES} />}</FormField>
            <FormField label="Status" hint="Closing requires corrective actions.">{(a) => <Select {...a} value={status} onChange={(e) => setStatus(e.target.value)} options={STATUSES} />}</FormField>
            <FormField label="Corrective actions" className="sm:col-span-2">{(a) => <Textarea {...a} rows={4} value={actions} onChange={(e) => setActions(e.target.value)} />}</FormField>
          </div>
        ) : (
          <>
            <p><span className="font-medium">Status:</span> <StatusBadge status={incident.status} /></p>
            {incident.correctiveActions && <p><span className="font-medium">Corrective actions:</span> {incident.correctiveActions}</p>}
          </>
        )}
      </div>
    </Dialog>
  );
}
