"use client";

import { useState } from "react";
import { useParams, useSearchParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Columns3, List, Plus } from "lucide-react";
import { toast } from "sonner";
import { Card, EmptyState, ErrorState, LoadingSkeleton } from "@/components/feedback/States";
import { DataTable, Pagination } from "@/components/ui/DataTable";
import { FilterBar, SearchField } from "@/components/ui/FilterBar";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { Checkbox, Select } from "@/components/forms/Fields";
import { Badge } from "@/components/ui/Badge";
import { useProject, useProjectMembers } from "@/features/projects/api";
import { TaskDialog } from "@/features/tasks/TaskDialog";
import { TaskForm } from "@/features/tasks/TaskForm";
import { taskColumns } from "@/features/tasks/taskColumns";
import { api, errorMessage } from "@/lib/api/client";
import { usePagedQuery } from "@/lib/api/hooks";
import { TASK_STATUSES } from "@/lib/constants/status";
import { cn } from "@/lib/utils/cn";
import { formatDate, humanize } from "@/lib/utils/format";

const BOARD_COLUMNS = ["TODO", "IN_PROGRESS", "BLOCKED", "DONE"];

export default function ProjectTasksPage() {
  const { projectId } = useParams();
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();
  const { data: project } = useProject(projectId);
  const { data: members = [] } = useProjectMembers(projectId);
  const [view, setView] = useState("table");
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("");
  const [assigneeId, setAssigneeId] = useState("");
  const [overdue, setOverdue] = useState(false);
  const [openTaskId, setOpenTaskId] = useState(searchParams.get("task"));
  const [creating, setCreating] = useState(false);

  const filters = { q, status, assigneeId, overdue: overdue || undefined };
  const list = usePagedQuery(["tasks", "project", projectId], `/projects/${projectId}/tasks`, { filters, size: 20, initialSort: "dueDate,asc", enabled: view === "table" });
  const board = useQuery({
    queryKey: ["tasks", "project", projectId, "board", { q, assigneeId, overdue }],
    queryFn: () => api.get(`/projects/${projectId}/tasks`, { q, assigneeId, overdue: overdue || undefined, size: 200, sort: "dueDate,asc" }),
    enabled: view === "board",
  });

  const canManage = project?.canManage && !["COMPLETED", "CANCELLED"].includes(project?.status);

  return (
    <>
      <Card>
        <FilterBar>
          <SearchField value={q} onChange={(v) => { setQ(v); list.setPage(0); }} placeholder="Search tasks" />
          {view === "table" && (
            <Select aria-label="Status" className="w-full sm:w-40" value={status} onChange={(e) => { setStatus(e.target.value); list.setPage(0); }} options={TASK_STATUSES} placeholder="All statuses" />
          )}
          <Select aria-label="Assignee" className="w-full sm:w-44" value={assigneeId} onChange={(e) => { setAssigneeId(e.target.value); list.setPage(0); }}
            options={members.map((m) => ({ value: m.userId, label: m.fullName }))} placeholder="Anyone" />
          <Checkbox label="Overdue only" checked={overdue} onChange={(e) => { setOverdue(e.target.checked); list.setPage(0); }} />
          <div className="ml-auto flex items-center gap-2">
            <div className="inline-flex rounded-md border border-border p-0.5" role="group" aria-label="View">
              <button type="button" aria-pressed={view === "table"} onClick={() => setView("table")}
                className={cn("rounded px-2 py-1 text-sm", view === "table" ? "bg-neutral-soft font-medium" : "text-muted")}>
                <List className="mr-1 inline h-4 w-4" aria-hidden="true" />Table
              </button>
              <button type="button" aria-pressed={view === "board"} onClick={() => setView("board")}
                className={cn("rounded px-2 py-1 text-sm", view === "board" ? "bg-neutral-soft font-medium" : "text-muted")}>
                <Columns3 className="mr-1 inline h-4 w-4" aria-hidden="true" />Board
              </button>
            </div>
            {canManage && <Button onClick={() => setCreating(true)}><Plus className="h-4 w-4" aria-hidden="true" /> New task</Button>}
          </div>
        </FilterBar>

        {view === "table" ? (
          <>
            <DataTable
              columns={taskColumns()}
              data={list.rows}
              loading={list.isPending}
              error={list.error}
              onRetry={list.refetch}
              sort={list.sort}
              onSortChange={list.setSort}
              onRowClick={(task) => setOpenTaskId(task.id)}
              empty={<EmptyState title="No tasks found" description={canManage ? "Create the first task for this project." : "Tasks will appear here when they are created."} />}
            />
            <Pagination page={list.page} size={list.size} totalPages={list.data?.totalPages} totalElements={list.data?.totalElements} onPageChange={list.setPage} />
          </>
        ) : board.error ? (
          <ErrorState error={board.error} onRetry={board.refetch} />
        ) : board.isPending ? (
          <LoadingSkeleton rows={4} />
        ) : (
          <div className="grid gap-4 p-4 md:grid-cols-2 xl:grid-cols-4">
            {BOARD_COLUMNS.map((column) => {
              const tasks = board.data.content.filter((t) => t.status === column);
              return (
                <section key={column} aria-label={humanize(column)} className="rounded-lg bg-surface-muted p-3">
                  <h3 className="mb-3 flex items-center justify-between text-xs font-semibold uppercase tracking-wide text-muted">
                    {humanize(column)} <span className="rounded-full bg-surface px-2 py-0.5">{tasks.length}</span>
                  </h3>
                  <ul className="space-y-2">
                    {tasks.map((task) => (
                      <li key={task.id}>
                        <button type="button" onClick={() => setOpenTaskId(task.id)}
                          className="w-full rounded-md border border-border bg-surface p-3 text-left shadow-sm hover:border-info">
                          <p className="text-sm font-medium">{task.title}</p>
                          <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-muted">
                            <span>{task.assignee?.fullName ?? "Unassigned"}</span>
                            {task.dueDate && <span className={task.overdue ? "font-medium text-danger" : ""}>· {formatDate(task.dueDate)}</span>}
                            {["HIGH", "CRITICAL"].includes(task.priority) && <Badge tone="danger">{humanize(task.priority)}</Badge>}
                          </div>
                        </button>
                      </li>
                    ))}
                    {tasks.length === 0 && <li className="py-4 text-center text-xs text-muted">No tasks</li>}
                  </ul>
                </section>
              );
            })}
          </div>
        )}
      </Card>

      <TaskDialog taskId={openTaskId} onClose={() => setOpenTaskId(null)} />

      <Dialog open={creating} onClose={() => setCreating(false)} title="New task" size="lg">
        <TaskForm
          projectId={projectId}
          onCancel={() => setCreating(false)}
          onSubmit={async (body) => {
            try {
              await api.post(`/projects/${projectId}/tasks`, body);
              await queryClient.invalidateQueries({ queryKey: ["tasks"] });
              await queryClient.invalidateQueries({ queryKey: ["projects"] });
              toast.success("Task created");
              setCreating(false);
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
