"use client";

import { flexRender, tableFeatures, useTable } from "@tanstack/react-table";
import { ArrowDown, ArrowUp, ChevronLeft, ChevronRight, ChevronsUpDown } from "lucide-react";
import { Button } from "./Button";
import { EmptyState, ErrorState, LoadingSkeleton } from "@/components/feedback/States";
import { cn } from "@/lib/utils/cn";

// Core features only: sorting and pagination are done by the API. Module scope keeps it stable (TanStack Table v9).
const features = tableFeatures({});

/**
 * Server-driven table: sorting and pagination happen in the API, so the table only renders the
 * current page. `sort` is the Spring Data string "field,asc|desc".
 * Columns opt in to sorting with `meta: { sortField: "createdAt" }`.
 */
export function DataTable({ columns, data = [], loading, error, onRetry, sort, onSortChange, empty, onRowClick, getRowId }) {
  const table = useTable({
    features,
    data,
    columns,
    getRowId: getRowId ?? ((row) => row.id),
  });

  if (error) return <ErrorState error={error} onRetry={onRetry} />;
  if (loading) return <LoadingSkeleton rows={5} />;
  if (data.length === 0) return empty ?? <EmptyState title="Nothing here yet" />;

  const [sortField, sortDir] = (sort ?? "").split(",");

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[640px] border-collapse text-sm">
        <thead>
          {table.getHeaderGroups().map((group) => (
            <tr key={group.id} className="border-b border-border bg-surface-muted">
              {group.headers.map((header) => {
                const field = header.column.columnDef.meta?.sortField;
                const active = field && field === sortField;
                const ariaSort = active ? (sortDir === "desc" ? "descending" : "ascending") : undefined;
                return (
                  <th
                    key={header.id}
                    scope="col"
                    aria-sort={ariaSort}
                    className={cn("px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-muted", header.column.columnDef.meta?.className)}
                  >
                    {field && onSortChange ? (
                      <button
                        type="button"
                        className="inline-flex items-center gap-1 hover:text-text"
                        onClick={() => onSortChange(`${field},${active && sortDir === "asc" ? "desc" : "asc"}`)}
                      >
                        {flexRender(header.column.columnDef.header, header.getContext())}
                        {active ? (
                          sortDir === "desc" ? <ArrowDown className="h-3 w-3" /> : <ArrowUp className="h-3 w-3" />
                        ) : (
                          <ChevronsUpDown className="h-3 w-3 opacity-50" />
                        )}
                      </button>
                    ) : (
                      flexRender(header.column.columnDef.header, header.getContext())
                    )}
                  </th>
                );
              })}
            </tr>
          ))}
        </thead>
        <tbody>
          {table.getRowModel().rows.map((row) => (
            <tr
              key={row.id}
              className={cn("border-b border-border last:border-0", onRowClick && "cursor-pointer hover:bg-surface-muted")}
              onClick={onRowClick ? () => onRowClick(row.original) : undefined}
            >
              {row.getAllCells().map((cell) => (
                <td key={cell.id} className={cn("px-4 py-3 align-middle", cell.column.columnDef.meta?.className)}>
                  {flexRender(cell.column.columnDef.cell, cell.getContext())}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function Pagination({ page, totalPages, totalElements, onPageChange, size }) {
  if (!totalPages || totalPages <= 1) {
    return totalElements ? <p className="px-4 py-3 text-xs text-muted">{totalElements} result{totalElements === 1 ? "" : "s"}</p> : null;
  }
  const from = page * size + 1;
  const to = Math.min((page + 1) * size, totalElements);
  return (
    <nav className="flex items-center justify-between gap-2 border-t border-border px-4 py-3" aria-label="Pagination">
      <p className="text-xs text-muted">
        {from}–{to} of {totalElements}
      </p>
      <div className="flex items-center gap-1">
        <Button variant="outline" size="sm" disabled={page === 0} onClick={() => onPageChange(page - 1)} aria-label="Previous page">
          <ChevronLeft className="h-4 w-4" />
        </Button>
        <span className="px-2 text-xs text-muted">
          Page {page + 1} of {totalPages}
        </span>
        <Button variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)} aria-label="Next page">
          <ChevronRight className="h-4 w-4" />
        </Button>
      </div>
    </nav>
  );
}
