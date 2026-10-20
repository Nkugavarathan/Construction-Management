# Architecture Decision Records

Each ADR records one significant decision: the context, the decision, and its consequences.
ADRs are never rewritten after acceptance. A changed decision gets a new ADR that supersedes the old one.

| ADR | Title | Status |
|---|---|---|
| [0001](0001-modular-monolith-mvc.md) | Modular monolith with feature-based MVC + service + repository | Proposed |
| [0002](0002-authentication-token-strategy.md) | In-memory access token, rotating HttpOnly refresh cookie, same-origin proxy | Proposed |
| [0003](0003-multi-tenancy-shared-schema.md) | Shared-schema multi-tenancy with application-level scoping | Proposed |
| [0004](0004-inventory-ledger-and-locking.md) | Inventory ledger with cached balance and pessimistic row lock | Proposed |
| [0005](0005-money-and-financial-immutability.md) | BigDecimal money and reversals instead of edits | Proposed |
| [0006](0006-idempotent-goods-receiving.md) | Idempotency key plus natural-key uniqueness for goods receipts | Proposed |
| [0007](0007-tanstack-query-for-server-state.md) | TanStack Query for frontend server state | Proposed |

Status moves to **Accepted** when Phase 0 is approved.
