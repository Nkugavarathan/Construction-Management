"use client";

import { ErrorState } from "@/components/feedback/States";

/** Last-resort boundary for unexpected render errors inside the signed-in area. */
export default function DashboardError({ error, retry }) {
  return <ErrorState error={error} onRetry={retry} className="rounded-lg border border-border bg-surface" />;
}
