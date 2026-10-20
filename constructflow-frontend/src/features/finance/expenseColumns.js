import { Badge, StatusBadge } from "@/components/ui/Badge";
import { formatDate, formatMoney } from "@/lib/utils/format";

export function expenseColumns(currency, { showProject = false } = {}) {
  return [
    {
      header: "Expense",
      cell: ({ row }) => (
        <div className="min-w-0">
          <p className="font-medium">{row.original.description}</p>
          <p className="text-xs text-muted">
            {row.original.number}
            {showProject && ` · ${row.original.project.code}`} · {row.original.category.name}
          </p>
        </div>
      ),
    },
    { header: "Date", meta: { sortField: "expenseDate" }, cell: ({ row }) => formatDate(row.original.expenseDate) },
    { header: "Recorded by", cell: ({ row }) => row.original.createdBy?.fullName },
    {
      header: "Status",
      meta: { sortField: "status" },
      cell: ({ row }) => (
        <div className="flex flex-wrap gap-1">
          <StatusBadge status={row.original.status} />
          {row.original.reversal && <Badge tone="info">Reversal</Badge>}
          {row.original.canDecide && <Badge tone="warning">Your decision</Badge>}
        </div>
      ),
    },
    {
      header: "Amount",
      meta: { sortField: "amount", className: "text-right" },
      cell: ({ row }) => <span className="block text-right font-medium tabular-nums">{formatMoney(row.original.amount, currency)}</span>,
    },
  ];
}
