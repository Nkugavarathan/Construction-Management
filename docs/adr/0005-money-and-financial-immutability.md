# ADR-0005: BigDecimal money and reversals instead of edits

- Status: Proposed
- Date: 2026-10-20

## Context
Budgets and expenses must add up exactly and be defensible to an auditor. Floating-point arithmetic cannot represent
values like 0.1 exactly. Silently editing approved amounts destroys the financial history.

## Decision
- Java `BigDecimal` with scale 2 and `RoundingMode.HALF_EVEN`; PostgreSQL `numeric(15,2)`. Quantities use `numeric(14,3)`.
  JSON serializes money as strings (`"125000.00"`) so JavaScript never parses it into a float.
- One currency per organization (ISO 4217 code in settings).
- Approved expenses are immutable. A correction is a **reversal** expense (negative amount, `reverses_expense_id` unique)
  that goes through the normal approval workflow. Metrics sum approved rows, so reversals net out automatically.
- Approval history is an append-only `expense_approvals` table plus audit events.

## Consequences
- Exact arithmetic and a complete trail. Reports can be re-derived for any date.
- Slightly more workflow for users who make mistakes. This is intended.
- Frontend formats money with `Intl.NumberFormat` from strings and does no arithmetic on amounts.
