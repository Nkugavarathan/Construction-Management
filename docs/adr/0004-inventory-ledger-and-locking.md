# ADR-0004: Inventory ledger with cached balance and pessimistic row lock

- Status: Proposed
- Date: 2026-10-20

## Context
Stock must never go negative, every change must be explainable, and two storekeepers (or a retrying client)
may issue the same remaining stock at the same moment.

## Decision
- `inventory_movements` is an append-only **ledger** (signed quantity, `balance_after`, actor, reason, reference).
- `materials.quantity_on_hand` is a **cached balance**, updated in the same transaction as the movement insert.
- Every stock-changing operation runs in one `@Transactional` method that:
  1. Loads the material with `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`),
  2. Validates the new balance is >= 0,
  3. Updates the balance and inserts the movement.
- Database `CHECK (quantity_on_hand >= 0)` as the final guard.
- A lock timeout (`jakarta.persistence.lock.timeout`) prevents indefinite waits.

## Consequences
- Concurrent issues serialize per material. A second request waits a few milliseconds, then sees the true balance.
- Reads of stock are a single column. Reconciliation (`SUM(movements) = quantity_on_hand`) is tested.
- Rejected: optimistic `@Version` alone. It is correct, but under contention it produces `409`s that users must retry,
  which is worse UX for a hot row. Computing the balance from the ledger on every read is rejected because it slows as history grows.
