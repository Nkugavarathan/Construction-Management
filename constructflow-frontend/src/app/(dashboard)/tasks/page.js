"use client";

import { useState } from "react";
import { ClipboardList } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, EmptyState } from "@/components/feedback/States";
import { DataTable, Pagination } from "@/components/ui/DataTable";
import { FilterBar } from "@/components/ui/FilterBar";
import { Checkbox } from "@/components/forms/Fields";
import { TaskDialog } from "@/features/tasks/TaskDialog";
import { taskColumns } from "@/features/tasks/taskColumns";
import { usePagedQuery } from "@/lib/api/hooks";

export default function MyTasksPage() {
  const [includeClosed, setIncludeClosed] = useState(false);
  const [openTaskId, setOpenTaskId] = useState(null);
  const list = usePagedQuery(["tasks", "mine"], "/tasks/mine", { filters: { includeClosed }, initialSort: "dueDate,asc" });

  return (
    <>
      <PageHeader title="My tasks" description="Work assigned to you across all projects in this organization." />
      <Card>
        <FilterBar>
          <Checkbox label="Include done and cancelled" checked={includeClosed} onChange={(e) => { setIncludeClosed(e.target.checked); list.setPage(0); }} />
        </FilterBar>
        <DataTable
          columns={taskColumns({ showProject: true })}
          data={list.rows}
          loading={list.isPending}
          error={list.error}
          onRetry={list.refetch}
          sort={list.sort}
          onSortChange={list.setSort}
          onRowClick={(task) => setOpenTaskId(task.id)}
          empty={<EmptyState icon={ClipboardList} title="Nothing assigned to you" description="Tasks assigned to you will appear here." />}
        />
        <Pagination page={list.page} size={list.size} totalPages={list.data?.totalPages} totalElements={list.data?.totalElements} onPageChange={list.setPage} />
      </Card>
      <TaskDialog taskId={openTaskId} onClose={() => setOpenTaskId(null)} />
    </>
  );
}
