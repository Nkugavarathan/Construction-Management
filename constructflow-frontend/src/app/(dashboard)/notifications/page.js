"use client";

import Link from "next/link";
import { useState } from "react";
import { Bell } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, EmptyState, ErrorState, LoadingSkeleton } from "@/components/feedback/States";
import { Pagination } from "@/components/ui/DataTable";
import { FilterBar } from "@/components/ui/FilterBar";
import { Button } from "@/components/ui/Button";
import { Checkbox } from "@/components/forms/Fields";
import { api } from "@/lib/api/client";
import { useApiMutation, usePagedQuery } from "@/lib/api/hooks";
import { formatDateTime } from "@/lib/utils/format";
import { cn } from "@/lib/utils/cn";

export default function NotificationsPage() {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const list = usePagedQuery(["notifications", "page"], "/notifications", { filters: { unreadOnly: unreadOnly || undefined } });
  const markAll = useApiMutation(() => api.post("/notifications/read-all"), { invalidate: [["notifications"]], success: "All marked as read" });
  const markOne = useApiMutation((id) => api.post(`/notifications/${id}/read`), { invalidate: [["notifications"]] });

  return (
    <>
      <PageHeader title="Notifications" actions={<Button variant="outline" loading={markAll.isPending} onClick={() => markAll.mutate()}>Mark all as read</Button>} />
      <Card>
        <FilterBar><Checkbox label="Unread only" checked={unreadOnly} onChange={(e) => { setUnreadOnly(e.target.checked); list.setPage(0); }} /></FilterBar>
        {list.error ? <ErrorState error={list.error} onRetry={list.refetch} /> : list.isPending ? <LoadingSkeleton /> : list.rows.length === 0 ? (
          <EmptyState icon={Bell} title="No notifications" description="Assignments, approvals and alerts will show up here." />
        ) : (
          <ul className="divide-y divide-border">
            {list.rows.map((n) => (
              <li key={n.id} className={cn("flex items-start gap-3 px-4 py-3", !n.read && "bg-info-soft/40")}>
                <span className={cn("mt-1.5 h-2 w-2 shrink-0 rounded-full", n.read ? "bg-border" : "bg-info")} aria-hidden="true" />
                <div className="min-w-0 flex-1">
                  {n.link ? (
                    <Link href={n.link} onClick={() => !n.read && markOne.mutate(n.id)} className={cn("hover:underline", !n.read && "font-medium")}>{n.title}</Link>
                  ) : <p className={cn(!n.read && "font-medium")}>{n.title}</p>}
                  {n.message && <p className="text-sm text-muted">{n.message}</p>}
                  <p className="text-xs text-muted">{formatDateTime(n.createdAt)}{!n.read && <span className="sr-only"> (unread)</span>}</p>
                </div>
                {!n.read && <Button size="sm" variant="ghost" onClick={() => markOne.mutate(n.id)}>Mark read</Button>}
              </li>
            ))}
          </ul>
        )}
        <Pagination page={list.page} size={list.size} totalPages={list.data?.totalPages} totalElements={list.data?.totalElements} onPageChange={list.setPage} />
      </Card>
    </>
  );
}
