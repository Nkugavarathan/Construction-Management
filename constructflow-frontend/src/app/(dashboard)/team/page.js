"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { UserPlus, Users } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, EmptyState } from "@/components/feedback/States";
import { DataTable, Pagination } from "@/components/ui/DataTable";
import { FilterBar, SearchField } from "@/components/ui/FilterBar";
import { Badge, StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { FormField, Select, SelectField, TextField } from "@/components/forms/Fields";
import { useAuth } from "@/features/auth/AuthProvider";
import { api, applyServerErrors } from "@/lib/api/client";
import { useApiMutation, usePagedQuery } from "@/lib/api/hooks";
import { ROLES } from "@/lib/constants/status";
import { formatDate, formatRelative, humanize } from "@/lib/utils/format";

const ROLE_HELP = {
  ADMIN: "Everything, including team and settings",
  PROJECT_MANAGER: "Runs projects they manage; approves small expenses",
  SITE_ENGINEER: "Updates assigned tasks, site reports, expenses",
  FINANCE_OFFICER: "Budgets, expense and PO approvals",
  STOREKEEPER: "Materials, stock, suppliers, receiving",
  VIEWER: "Read-only access to their projects",
};

const schema = z.object({
  fullName: z.string().trim().min(1, "Required").max(160),
  email: z.email("Enter a valid email"),
  role: z.enum(ROLES),
  temporaryPassword: z.string().min(12, "At least 12 characters").max(128),
});

export default function TeamPage() {
  const { can, me } = useAuth();
  const [q, setQ] = useState("");
  const [role, setRole] = useState("");
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState(null);
  const list = usePagedQuery(["members"], "/members", { filters: { q, role } });
  const manage = can("MEMBER_MANAGE");

  const columns = [
    { header: "Name", cell: ({ row }) => (<div><p className="font-medium">{row.original.user.fullName}{row.original.user.id === me.user.id && <span className="ml-1 text-xs text-muted">(you)</span>}</p><p className="text-xs text-muted">{row.original.user.email}</p></div>) },
    { header: "Role", cell: ({ row }) => <Badge tone={row.original.role === "ADMIN" ? "brand" : "neutral"}>{humanize(row.original.role)}</Badge> },
    { header: "Status", cell: ({ row }) => row.original.status === "ACTIVE" ? <Badge tone="success">Active</Badge> : <StatusBadge status={row.original.status} /> },
    { header: "Last login", cell: ({ row }) => row.original.lastLoginAt ? formatRelative(row.original.lastLoginAt) : <span className="text-muted">Never</span> },
    { header: "Joined", cell: ({ row }) => formatDate(row.original.joinedAt) },
  ];

  return (
    <>
      <PageHeader title="Team" description="People in this organization and their roles."
        actions={manage && <Button onClick={() => setAdding(true)}><UserPlus className="h-4 w-4" aria-hidden="true" /> Add member</Button>} />
      <Card>
        <FilterBar>
          <SearchField value={q} onChange={(v) => { setQ(v); list.setPage(0); }} placeholder="Search name or email" />
          <Select aria-label="Role" className="w-full sm:w-48" value={role} onChange={(e) => { setRole(e.target.value); list.setPage(0); }} options={ROLES} placeholder="All roles" />
        </FilterBar>
        <DataTable columns={columns} data={list.rows} loading={list.isPending} error={list.error} onRetry={list.refetch}
          onRowClick={manage ? (row) => setEditing(row) : undefined} empty={<EmptyState icon={Users} title="No members found" />} />
        <Pagination page={list.page} size={list.size} totalPages={list.data?.totalPages} totalElements={list.data?.totalElements} onPageChange={list.setPage} />
      </Card>
      <Dialog open={adding} onClose={() => setAdding(false)} title="Add team member"
        description="They'll sign in with this temporary password and must choose their own at first login.">
        {adding && <AddMemberForm onDone={() => setAdding(false)} />}
      </Dialog>
      {editing && <EditMemberDialog member={editing} onClose={() => setEditing(null)} />}
    </>
  );
}

function AddMemberForm({ onDone }) {
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(schema), defaultValues: { fullName: "", email: "", role: "SITE_ENGINEER", temporaryPassword: "" },
  });
  const add = useApiMutation((body) => api.post("/members", body), { invalidate: [["members"]], success: "Member added", onSuccess: onDone });
  return (
    <form className="space-y-4" noValidate onSubmit={handleSubmit(async (v) => {
      try { await add.mutateAsync(v); } catch (e) { applyServerErrors(e, setError); }
    })}>
      <TextField label="Full name" register={register("fullName")} error={errors.fullName?.message} required />
      <TextField label="Email" type="email" register={register("email")} error={errors.email?.message} required />
      <SelectField label="Role" options={ROLES.map((r) => ({ value: r, label: `${humanize(r)} — ${ROLE_HELP[r]}` }))} register={register("role")} error={errors.role?.message} required />
      <TextField label="Temporary password" type="password" autoComplete="new-password" hint="Share it securely; at least 12 characters." register={register("temporaryPassword")} error={errors.temporaryPassword?.message} required />
      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onDone}>Cancel</Button>
        <Button type="submit" loading={isSubmitting}>Add member</Button>
      </div>
    </form>
  );
}

function EditMemberDialog({ member, onClose }) {
  const [role, setRole] = useState(member.role);
  const [status, setStatus] = useState(member.status);
  const save = useApiMutation(() => api.patch(`/members/${member.id}`, { role, status, version: member.version }),
    { invalidate: [["members"]], success: "Member updated", onSuccess: onClose });
  return (
    <Dialog open onClose={onClose} title={member.user.fullName} description={member.user.email}
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button loading={save.isPending} disabled={role === member.role && status === member.status} onClick={() => save.mutate()}>Save</Button></>}>
      <div className="space-y-4">
        <FormField label="Role" hint={ROLE_HELP[role]}>{(a) => <Select {...a} value={role} onChange={(e) => setRole(e.target.value)} options={ROLES} />}</FormField>
        <FormField label="Status" hint="Disabled members are signed out on their next request.">
          {(a) => <Select {...a} value={status} onChange={(e) => setStatus(e.target.value)} options={["ACTIVE", "DISABLED"]} />}
        </FormField>
        <p className="text-xs text-muted">Role and status changes are recorded in the audit log. An organization always keeps at least one active admin.</p>
      </div>
    </Dialog>
  );
}
