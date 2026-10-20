# ADR-0006: Idempotency key plus natural-key uniqueness for goods receipts

- Status: Proposed (implemented in Phase 6)
- Date: 2026-10-20

## Context
Receiving a delivery increases stock. A network retry, a double-click, or two people entering the same delivery
note would inflate stock and misstate what the supplier has delivered.

## Decision
- `POST /purchase-orders/{id}/receipts` requires an `Idempotency-Key` header (UUID generated when the receive dialog opens).
  `goods_receipts` has `UNIQUE (organization_id, idempotency_key)`. A repeated key returns the **original** receipt (`200`)
  instead of creating a new one. The same key with a different payload returns `409`.
- `UNIQUE (purchase_order_id, delivery_note_ref)` prevents the same physical delivery note being booked twice.
- The PO row is locked (`FOR UPDATE`) during receiving, and each line validates `received + qty <= ordered`.
- PO status, receipt, line quantities, inventory movements and the audit event commit in **one transaction**.

## Consequences
- Retries are safe; over-receiving is impossible even under concurrency.
- Clients must send the header. The frontend API client handles this for the receive action.
