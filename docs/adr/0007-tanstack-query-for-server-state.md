# ADR-0007: TanStack Query for frontend server state

- Status: Proposed
- Date: 2026-10-20

## Context
Most pages load paginated, filtered API data and must show loading, error, empty and refreshed states correctly,
including invalidating lists after mutations (for example, approving an expense must refresh the queue and the project summary).

## Decision
Use `@tanstack/react-query` for all server data. API functions live in `features/<feature>/api.js`, and hooks in
`features/<feature>/hooks.js`. Query keys include filters and pagination. Mutations invalidate the affected keys.
No global client state library (Redux/Zustand): auth lives in a small React context, and UI state stays local.

## Consequences
- Removes hand-written `useEffect` fetch logic, a common source of race conditions and stale data.
- One extra dependency (outside the mandated list). It is widely used and easy to justify in interviews.
- Rejected: plain `useEffect` (bug-prone) and SWR (fine, but TanStack Query's mutation and invalidation API fits our workflows better).
