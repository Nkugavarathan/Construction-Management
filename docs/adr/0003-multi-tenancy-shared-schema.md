# ADR-0003: Shared-schema multi-tenancy with application-level scoping

- Status: Proposed
- Date: 2026-10-20

## Context
Multiple construction companies share one deployment. Data leakage between organizations is the most serious
failure mode the system can have.

## Decision
- One schema. Every tenant-owned table has `organization_id uuid not null`, and composite indexes lead with it.
- The active organization comes **only** from the authenticated principal (JWT `org` claim, verified against an ACTIVE
  membership on each request). It is never taken from a request body, query or path.
- Repositories expose tenant-scoped lookups (`findByIdAndOrganizationId`). Child resources are reached through their
  tenant-checked parent.
- A resource in another organization returns **404**, the same response as a resource that does not exist.
- Every resource type gets a cross-tenant integration test (org A user attempts to read and write org B's resource).

## Consequences
- Simple migrations and queries; works with ordinary connection pooling.
- Correctness depends on developer discipline, which the tests enforce. A forgotten filter is the main risk.
- Future hardening: PostgreSQL Row-Level Security with `SET LOCAL app.org_id` per transaction as defense-in-depth.
- Rejected: schema-per-tenant or database-per-tenant (migration fan-out, overkill for SMB scale).
