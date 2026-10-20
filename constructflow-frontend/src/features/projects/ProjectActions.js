"use client";

import { useState } from "react";
import { ChevronDown, Pencil } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { Menu, MenuItem } from "@/components/ui/Menu";
import { FormField, Textarea } from "@/components/forms/Fields";
import { api, errorMessage } from "@/lib/api/client";
import { useApiMutation } from "@/lib/api/hooks";
import { humanize } from "@/lib/utils/format";
import { ProjectForm } from "./ProjectForm";

const TRANSITION_LABELS = {
  ACTIVE: "Start / resume project",
  ON_HOLD: "Put on hold",
  COMPLETED: "Mark completed",
  CANCELLED: "Cancel project",
};

/**
 * Lifecycle and edit actions. The allowed transitions come from the API (project.allowedTransitions),
 * so the UI never re-implements the state machine; it only offers what the server will accept.
 */
export function ProjectActions({ project }) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [target, setTarget] = useState(null);
  const [reason, setReason] = useState("");

  const transition = useApiMutation(
    (body) => api.post(`/projects/${project.id}/transitions`, body),
    {
      invalidate: [["projects"], ["dashboard"]],
      success: (data) => `Project is now ${humanize(data.status).toLowerCase()}`,
      onSuccess: () => {
        setTarget(null);
        setReason("");
      },
    },
  );

  if (!project.canManage) return null;
  const needsReason = target === "ON_HOLD" || target === "CANCELLED";

  return (
    <>
      {project.status !== "COMPLETED" && project.status !== "CANCELLED" && (
        <Button variant="outline" onClick={() => setEditing(true)}>
          <Pencil className="h-4 w-4" aria-hidden="true" /> Edit
        </Button>
      )}
      {project.allowedTransitions.length > 0 && (
        <Menu
          trigger={(props) => (
            <Button {...props}>
              Change status <ChevronDown className="h-4 w-4" aria-hidden="true" />
            </Button>
          )}
        >
          {project.allowedTransitions.map((status) => (
            <MenuItem key={status} onClick={() => setTarget(status)} className={status === "CANCELLED" ? "text-danger" : ""}>
              {TRANSITION_LABELS[status] ?? humanize(status)}
            </MenuItem>
          ))}
        </Menu>
      )}

      <Dialog
        open={Boolean(target)}
        onClose={() => setTarget(null)}
        title={TRANSITION_LABELS[target] ?? ""}
        description={
          target === "COMPLETED"
            ? "All tasks must be done or cancelled and no expense may be awaiting approval. Completed projects become read-only."
            : target === "CANCELLED"
              ? "Cancelled projects become read-only. This cannot be undone."
              : undefined
        }
        footer={
          <>
            <Button variant="outline" onClick={() => setTarget(null)}>Back</Button>
            <Button
              variant={target === "CANCELLED" ? "danger" : "primary"}
              loading={transition.isPending}
              disabled={needsReason && !reason.trim()}
              onClick={() => transition.mutate({ target, reason: reason.trim() || null, version: project.version })}
            >
              Confirm
            </Button>
          </>
        }
      >
        {needsReason ? (
          <FormField label="Reason" required hint="Recorded in the project history and audit log.">
            {(a11y) => <Textarea {...a11y} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />}
          </FormField>
        ) : (
          <p className="text-sm text-muted">Move {project.code} from {humanize(project.status)} to {humanize(target)}?</p>
        )}
      </Dialog>

      <Dialog open={editing} onClose={() => setEditing(false)} title={`Edit ${project.code}`} size="lg">
        <ProjectForm
          project={project}
          submitLabel="Save changes"
          onCancel={() => setEditing(false)}
          onSubmit={async (values) => {
            try {
              await api.patch(`/projects/${project.id}`, { ...values, version: project.version });
              await queryClient.invalidateQueries({ queryKey: ["projects"] });
              toast.success("Project updated");
              setEditing(false);
            } catch (error) {
              toast.error(errorMessage(error));
              throw error;
            }
          }}
        />
      </Dialog>
    </>
  );
}
