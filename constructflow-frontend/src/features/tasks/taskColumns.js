import { Badge, StatusBadge } from "@/components/ui/Badge";
import { formatDate, humanize } from "@/lib/utils/format";

export function taskColumns({ showProject = false } = {}) {
  return [
    {
      header: "Task",
      meta: { sortField: "title" },
      cell: ({ row }) => (
        <div className="min-w-0">
          <p className="font-medium">{row.original.title}</p>
          <p className="text-xs text-muted">
            {showProject ? `${row.original.project.code} · ` : ""}
            {row.original.milestone?.name ?? "No milestone"}
          </p>
        </div>
      ),
    },
    { header: "Status", meta: { sortField: "status" }, cell: ({ row }) => <StatusBadge status={row.original.status} /> },
    {
      header: "Priority",
      cell: ({ row }) => <Badge tone={["HIGH", "CRITICAL"].includes(row.original.priority) ? "danger" : "neutral"}>{humanize(row.original.priority)}</Badge>,
    },
    { header: "Assignee", cell: ({ row }) => row.original.assignee?.fullName ?? <span className="text-muted">Unassigned</span> },
    {
      header: "Due",
      meta: { sortField: "dueDate" },
      cell: ({ row }) => (
        <span className={row.original.overdue ? "font-medium text-danger" : ""}>
          {formatDate(row.original.dueDate)}
          {row.original.overdue && <span className="sr-only"> (overdue)</span>}
        </span>
      ),
    },
  ];
}
