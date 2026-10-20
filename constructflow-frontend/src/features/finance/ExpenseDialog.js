"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Badge, StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { FormField, Textarea } from "@/components/forms/Fields";
import { ErrorState, LoadingSkeleton } from "@/components/feedback/States";
import { useAuth } from "@/features/auth/AuthProvider";
import { api } from "@/lib/api/client";
import { useApiMutation } from "@/lib/api/hooks";
import { formatDate, formatDateTime, formatMoney, humanize } from "@/lib/utils/format";
import { ExpenseForm } from "./ExpenseForm";

const FINANCE_KEYS = [["expenses"], ["finance"], ["dashboard"], ["projects"]];

/**
 * Expense details and actions. Which buttons appear comes from the server: `canDecide` already
 * applies segregation of duties and the approval limit, so the UI never guesses.
 */
export function ExpenseDialog({ expenseId, onClose }) {
  const { me, can } = useAuth();
  const currency = me.activeOrganization.currencyCode;
  const [mode, setMode] = useState(null); // "edit" | "reject" | "approve" | "reverse"
  const [comment, setComment] = useState("");
  const enabled = Boolean(expenseId);
  const expense = useQuery({ queryKey: ["expenses", "detail", expenseId], queryFn: () => api.get(`/expenses/${expenseId}`), enabled });
  const history = useQuery({ queryKey: ["expenses", "history", expenseId], queryFn: () => api.get(`/expenses/${expenseId}/history`), enabled });

  // Read nested data once, safely: the React Compiler evaluates closure dependencies during render,
  // so `expense.data.version` inside a callback would throw while the expense is still loading.
  const version = expense.data?.version;
  const done = () => { setMode(null); setComment(""); };
  const opts = (success) => ({ invalidate: FINANCE_KEYS, success, onSuccess: done, onError: (e) => e.code === "STALE_VERSION" && expense.refetch() });
  const submit = useApiMutation(() => api.post(`/expenses/${expenseId}/submit`), opts("Submitted for approval"));
  const approve = useApiMutation(() => api.post(`/expenses/${expenseId}/approve`, { version, comment: comment || null }), opts("Expense approved"));
  const reject = useApiMutation(() => api.post(`/expenses/${expenseId}/reject`, { version, comment }), opts("Expense rejected"));
  const reverse = useApiMutation(() => api.post(`/expenses/${expenseId}/reversal`, { reason: comment }), opts("Reversal created and sent for approval"));
  const remove = useApiMutation(() => api.delete(`/expenses/${expenseId}`), { invalidate: FINANCE_KEYS, success: "Draft deleted", onSuccess: onClose });

  const e = expense.data;
  const mine = e && e.createdBy?.id === me.user.id;
  const canReverse = e && e.status === "APPROVED" && !e.reversal && (me.role === "ADMIN" || me.role === "FINANCE_OFFICER");

  return (
    <Dialog open={enabled} onClose={() => { done(); onClose(); }} title={e ? `${e.number} · ${e.description}` : "Expense"} size="lg">
      {expense.error ? <ErrorState error={expense.error} onRetry={expense.refetch} /> : !e ? <LoadingSkeleton rows={4} /> : mode === "edit" ? (
        <ExpenseForm expense={e} submitLabel="Save changes" onCancel={done}
          onSubmit={async (values) => {
            await api.patch(`/expenses/${e.id}`, values);
            await expense.refetch();
            done();
          }} />
      ) : (
        <div className="space-y-5">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={e.status} />
            {e.reversal && <Badge tone="info">Reversal</Badge>}
            <span className="text-xs text-muted">{e.project.code} · {e.project.name}</span>
          </div>
          <p className="text-3xl font-semibold tabular-nums">{formatMoney(e.amount, currency)}</p>
          <dl className="grid gap-3 text-sm sm:grid-cols-3">
            <Item label="Category" value={e.category.name} />
            <Item label="Vendor" value={e.vendorName ?? "—"} />
            <Item label="Expense date" value={formatDate(e.expenseDate)} />
            <Item label="Recorded by" value={e.createdBy?.fullName} />
            <Item label="Submitted" value={e.submittedAt ? formatDateTime(e.submittedAt) : "—"} />
            <Item label="Decided by" value={e.decidedBy ? `${e.decidedBy.fullName}, ${formatDateTime(e.decidedAt)}` : "—"} />
          </dl>

          <section aria-labelledby="history-heading">
            <h3 id="history-heading" className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted">Approval history</h3>
            {history.data?.length ? (
              <ol className="space-y-2 border-l-2 border-border pl-4">
                {history.data.map((h, i) => (
                  <li key={i} className="text-sm">
                    <span className="font-medium">{humanize(h.action)}</span> by {h.actor.fullName}
                    <span className="text-muted"> · {formatDateTime(h.at)}</span>
                    {h.comment && <p className="text-muted">“{h.comment}”</p>}
                  </li>
                ))}
              </ol>
            ) : <p className="text-sm text-muted">Not submitted yet.</p>}
          </section>

          {mode === "approve" || mode === "reject" || mode === "reverse" ? (
            <div className="space-y-3 rounded-md border border-border p-3">
              <FormField
                label={mode === "approve" ? "Comment (optional)" : mode === "reject" ? "Reason for rejection" : "Reason for reversal"}
                required={mode !== "approve"}
                hint={mode === "reverse" ? "Creates a negative expense linked to this one. It needs approval from someone else." : undefined}
              >
                {(a11y) => <Textarea {...a11y} value={comment} onChange={(ev) => setComment(ev.target.value)} maxLength={1000} />}
              </FormField>
              <div className="flex justify-end gap-2">
                <Button variant="outline" size="sm" onClick={done}>Back</Button>
                {mode === "approve" && <Button size="sm" variant="secondary" loading={approve.isPending} onClick={() => approve.mutate()}>Approve</Button>}
                {mode === "reject" && <Button size="sm" variant="danger" disabled={!comment.trim()} loading={reject.isPending} onClick={() => reject.mutate()}>Reject</Button>}
                {mode === "reverse" && <Button size="sm" disabled={!comment.trim()} loading={reverse.isPending} onClick={() => reverse.mutate()}>Create reversal</Button>}
              </div>
            </div>
          ) : (
            <div className="flex flex-wrap gap-2 border-t border-border pt-4">
              {e.status === "DRAFT" && mine && (
                <>
                  <Button size="sm" loading={submit.isPending} onClick={() => submit.mutate()}>Submit for approval</Button>
                  <Button size="sm" variant="outline" onClick={() => setMode("edit")}>Edit</Button>
                  <Button size="sm" variant="ghost" loading={remove.isPending} onClick={() => remove.mutate()}>Delete draft</Button>
                </>
              )}
              {e.canDecide && (
                <>
                  <Button size="sm" variant="secondary" onClick={() => setMode("approve")}>Approve</Button>
                  <Button size="sm" variant="outline" onClick={() => setMode("reject")}>Reject</Button>
                </>
              )}
              {e.status === "SUBMITTED" && !e.canDecide && can("EXPENSE_APPROVE") && (
                <p className="text-sm text-muted">
                  {e.submittedBy?.id === me.user.id ? "You submitted this expense, so someone else must decide on it." : "This expense is outside your approval authority."}
                </p>
              )}
              {canReverse && <Button size="sm" variant="outline" onClick={() => setMode("reverse")}>Reverse…</Button>}
            </div>
          )}
        </div>
      )}
    </Dialog>
  );
}

function Item({ label, value }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-muted">{label}</dt>
      <dd className="mt-0.5">{value}</dd>
    </div>
  );
}
