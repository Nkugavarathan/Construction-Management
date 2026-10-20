"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { Badge, StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { FormField, Textarea } from "@/components/forms/Fields";
import { ErrorState, LoadingSkeleton } from "@/components/feedback/States";
import { api, errorMessage } from "@/lib/api/client";
import { useApiMutation } from "@/lib/api/hooks";
import { formatDate, formatDateTime, humanize } from "@/lib/utils/format";
import { TaskForm } from "./TaskForm";

const ACTION_LABELS = {
  IN_PROGRESS: "Start",
  BLOCKED: "Mark blocked",
  DONE: "Mark done",
  TODO: "Move to to-do",
  CANCELLED: "Cancel task",
};

const NEEDS_REASON = new Set(["BLOCKED", "CANCELLED"]);

/** Task details with the status changes the server says this user may make. */
export function TaskDialog({ taskId, onClose }) {
  const [editing, setEditing] = useState(false);
  const [pendingTarget, setPendingTarget] = useState(null);
  const [reason, setReason] = useState("");
  const { data: task, error, isPending, refetch } = useQuery({
    queryKey: ["tasks", "detail", taskId],
    queryFn: () => api.get(`/tasks/${taskId}`),
    enabled: Boolean(taskId),
  });

  const invalidate = [["tasks"], ["projects"], ["dashboard"], ["milestones"]];
  const transition = useApiMutation((body) => api.post(`/tasks/${taskId}/transitions`, body), {
    invalidate,
    success: (data) => `Task moved to ${humanize(data.status).toLowerCase()}`,
    onSuccess: () => {
      setPendingTarget(null);
      setReason("");
    },
    onError: (err) => {
      if (err.code === "STALE_VERSION") refetch();
    },
  });

  const version = task?.version;
  const run = (target) => {
    if (NEEDS_REASON.has(target)) {
      setPendingTarget(target);
    } else {
      transition.mutate({ target, version });
    }
  };

  return (
    <Dialog open={Boolean(taskId)} onClose={onClose} title={task?.title ?? "Task"} size="lg">
      {error ? (
        <ErrorState error={error} onRetry={refetch} />
      ) : isPending ? (
        <LoadingSkeleton rows={4} />
      ) : editing ? (
        <TaskForm
          projectId={task.project.id}
          task={task}
          onCancel={() => setEditing(false)}
          onSubmit={async (body) => {
            try {
              await api.patch(`/tasks/${task.id}`, body);
              await Promise.all([refetch()]);
              toast.success("Task updated");
              setEditing(false);
            } catch (err) {
              toast.error(errorMessage(err));
              throw err;
            }
          }}
        />
      ) : (
        <div className="space-y-5">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={task.status} />
            <Badge tone={task.priority === "CRITICAL" || task.priority === "HIGH" ? "danger" : "neutral"}>{humanize(task.priority)} priority</Badge>
            {task.overdue && <Badge tone="danger">Overdue</Badge>}
            <span className="text-xs text-muted">{task.project.code}</span>
          </div>
          {task.statusReason && <p className="rounded-md bg-warning-soft px-3 py-2 text-sm text-warning">{task.statusReason}</p>}
          <dl className="grid gap-3 text-sm sm:grid-cols-3">
            <Detail label="Assignee" value={task.assignee?.fullName ?? "Unassigned"} />
            <Detail label="Milestone" value={task.milestone?.name ?? "-"} />
            <Detail label="Weight" value={task.weight} />
            <Detail label="Start" value={formatDate(task.startDate)} />
            <Detail label="Due" value={formatDate(task.dueDate)} />
            <Detail label="Completed" value={task.completedAt ? formatDateTime(task.completedAt) : "-"} />
          </dl>
          {task.description && <p className="whitespace-pre-line text-sm text-muted">{task.description}</p>}

          {pendingTarget ? (
            <div className="space-y-3 rounded-md border border-border p-3">
              <FormField label={`Why is this task ${humanize(pendingTarget).toLowerCase()}?`} required>
                {(a11y) => <Textarea {...a11y} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />}
              </FormField>
              <div className="flex justify-end gap-2">
                <Button variant="outline" size="sm" onClick={() => setPendingTarget(null)}>Back</Button>
                <Button size="sm" disabled={!reason.trim()} loading={transition.isPending}
                  onClick={() => transition.mutate({ target: pendingTarget, reason: reason.trim(), version })}>
                  Confirm
                </Button>
              </div>
            </div>
          ) : (
            <div className="flex flex-wrap items-center gap-2 border-t border-border pt-4">
              {task.allowedTransitions.length === 0 && !task.canEdit && (
                <p className="text-sm text-muted">You can view this task but not change it.</p>
              )}
              {task.allowedTransitions.map((target) => (
                <Button key={target} size="sm" variant={target === "DONE" ? "secondary" : target === "CANCELLED" ? "outline" : "primary"}
                  loading={transition.isPending && transition.variables?.target === target} onClick={() => run(target)}>
                  {ACTION_LABELS[target] ?? humanize(target)}
                </Button>
              ))}
              {task.canEdit && <Button size="sm" variant="ghost" onClick={() => setEditing(true)}>Edit details</Button>}
            </div>
          )}
        </div>
      )}
    </Dialog>
  );
}

function Detail({ label, value }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-muted">{label}</dt>
      <dd className="mt-0.5">{value}</dd>
    </div>
  );
}
