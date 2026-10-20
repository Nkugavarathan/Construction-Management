"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { UserPlus, Users } from "lucide-react";
import { Card, EmptyState, ErrorState, LoadingSkeleton } from "@/components/feedback/States";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog, Dialog } from "@/components/ui/Dialog";
import { FormField, Select } from "@/components/forms/Fields";
import { useOrganizationMembers, useProject, useProjectMembers } from "@/features/projects/api";
import { api } from "@/lib/api/client";
import { useApiMutation } from "@/lib/api/hooks";
import { formatDate, humanize } from "@/lib/utils/format";

export default function ProjectMembersPage() {
  const { projectId } = useParams();
  const { data: project } = useProject(projectId);
  const { data: members, error, isPending, refetch } = useProjectMembers(projectId);
  const [adding, setAdding] = useState(false);
  const [removing, setRemoving] = useState(null);
  const canManage = project?.canManage && !["COMPLETED", "CANCELLED"].includes(project?.status);

  const remove = useApiMutation((userId) => api.delete(`/projects/${projectId}/members/${userId}`), {
    invalidate: [["projects"]], success: "Removed from project", onSuccess: () => setRemoving(null),
  });

  return (
    <Card
      title="Project team"
      description="Only project members (and organization-wide roles) can see this project."
      actions={canManage && <Button size="sm" onClick={() => setAdding(true)}><UserPlus className="h-4 w-4" aria-hidden="true" /> Add member</Button>}
    >
      {error ? <ErrorState error={error} onRetry={refetch} /> : isPending ? <LoadingSkeleton /> : members.length === 0 ? (
        <EmptyState icon={Users} title="No members" />
      ) : (
        <ul className="divide-y divide-border">
          {members.map((m) => (
            <li key={m.userId} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
              <div>
                <p className="font-medium">{m.fullName}</p>
                <p className="text-xs text-muted">{m.email} · {humanize(m.organizationRole)} · added {formatDate(m.addedAt)}</p>
              </div>
              <div className="flex items-center gap-2">
                <Badge tone={m.projectRole === "MANAGER" ? "brand" : "neutral"}>{m.projectRole === "MANAGER" ? "Project manager" : "Member"}</Badge>
                {canManage && m.userId !== project.manager?.id && (
                  <Button size="sm" variant="ghost" onClick={() => setRemoving(m)}>Remove</Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
      <AddMemberDialog open={adding} onClose={() => setAdding(false)} projectId={projectId} existing={members ?? []} />
      <ConfirmDialog
        open={Boolean(removing)}
        onClose={() => setRemoving(null)}
        onConfirm={() => remove.mutate(removing.userId)}
        loading={remove.isPending}
        title="Remove from project?"
        confirmLabel="Remove"
        tone="danger"
      >
        <p className="text-sm text-muted">{removing?.fullName} will lose access to this project. People with open tasks must have them reassigned first.</p>
      </ConfirmDialog>
    </Card>
  );
}

function AddMemberDialog({ open, onClose, projectId, existing }) {
  const { data: orgMembers = [] } = useOrganizationMembers(open);
  const [userId, setUserId] = useState("");
  const [projectRole, setProjectRole] = useState("MEMBER");
  const add = useApiMutation((body) => api.post(`/projects/${projectId}/members`, body), {
    invalidate: [["projects"]], success: "Added to project",
    onSuccess: () => { setUserId(""); setProjectRole("MEMBER"); onClose(); },
  });
  const existingIds = new Set(existing.map((m) => m.userId));
  const candidates = orgMembers.filter((m) => !existingIds.has(m.user.id));
  const selected = orgMembers.find((m) => m.user.id === userId);
  const canBeManager = selected && ["PROJECT_MANAGER", "ADMIN"].includes(selected.role);

  return (
    <Dialog open={open} onClose={onClose} title="Add project member"
      footer={<>
        <Button variant="outline" onClick={onClose}>Cancel</Button>
        <Button disabled={!userId} loading={add.isPending} onClick={() => add.mutate({ userId, projectRole: canBeManager ? projectRole : "MEMBER" })}>Add</Button>
      </>}>
      <div className="space-y-4">
        <FormField label="Person" required>
          {(a11y) => (
            <Select {...a11y} value={userId} onChange={(e) => setUserId(e.target.value)} placeholder="Choose a team member"
              options={candidates.map((m) => ({ value: m.user.id, label: `${m.user.fullName} — ${humanize(m.role)}` }))} />
          )}
        </FormField>
        <FormField label="Project role" hint={canBeManager ? undefined : "Only project managers and admins can manage a project."}>
          {(a11y) => (
            <Select {...a11y} value={canBeManager ? projectRole : "MEMBER"} disabled={!canBeManager} onChange={(e) => setProjectRole(e.target.value)}
              options={[{ value: "MEMBER", label: "Member" }, { value: "MANAGER", label: "Project manager" }]} />
          )}
        </FormField>
      </div>
    </Dialog>
  );
}
