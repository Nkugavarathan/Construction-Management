# ADR-0001: Modular monolith with feature-based MVC + service + repository

- Status: Proposed
- Date: 2026-10-20

## Context
ConstructFlow has ~12 business modules, one small team (one developer), and workflows that must change several
modules atomically. For example, receiving a purchase order updates the PO, the goods receipt, the inventory
ledger, stock balances and the audit log together.

## Decision
- Build **one Spring Boot application** packaged by feature (`project`, `task`, `finance`, `inventory`, ...).
  Each feature contains only the layers it needs: `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `exception`.
- A module may call another module's **service**, never its repository or entity setters.
- Controllers are thin. Services own transactions and business rules. Repositories own queries.
- No interface-per-service unless there are two real implementations (e.g. `FileStorage`: local and S3).

## Consequences
- Cross-module workflows get real ACID transactions with `@Transactional`, with no distributed sagas.
- One build, one deploy, one log stream: easy to run locally and explain in interviews.
- Risk: modules can become tangled. Mitigation: the service-only rule plus code review. ArchUnit tests can enforce it later.
- Extraction to a separate service later is possible because module boundaries already exist.
- Rejected: microservices (operational cost with no scaling need) and layer-first packages (`controllers/`, `services/`, ...),
  which scatter one feature across the tree.
