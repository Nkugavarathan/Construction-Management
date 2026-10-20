create table materials (
    id                uuid primary key,
    organization_id   uuid          not null references organizations (id),
    code              varchar(32)   not null,
    name              varchar(160)  not null,
    unit              varchar(16)   not null check (unit in ('BAG', 'KG', 'TONNE', 'M', 'M2', 'M3', 'LITRE', 'PIECE', 'BOX', 'ROLL', 'SHEET')),
    reorder_level     numeric(14,3) not null default 0 check (reorder_level >= 0),
    -- Cached balance of the movement ledger, updated in the same transaction as each movement.
    quantity_on_hand  numeric(14,3) not null default 0 check (quantity_on_hand >= 0),
    active            boolean       not null default true,
    version           bigint        not null default 0,
    created_at        timestamptz   not null default now(),
    updated_at        timestamptz   not null default now(),
    constraint uq_material_code unique (organization_id, code)
);

-- Append-only stock ledger: the source of truth for every quantity change.
create table inventory_movements (
    id               uuid primary key,
    organization_id  uuid          not null references organizations (id),
    material_id      uuid          not null references materials (id),
    project_id       uuid references projects (id),
    type             varchar(16)   not null check (type in ('RECEIPT', 'ISSUE', 'RETURN', 'ADJUSTMENT')),
    quantity         numeric(14,3) not null check (quantity <> 0),
    balance_after    numeric(14,3) not null check (balance_after >= 0),
    reason           varchar(500),
    reference_type   varchar(32),
    reference_id     uuid,
    actor_id         uuid          not null references users (id),
    occurred_at      timestamptz   not null default now(),
    constraint ck_movement_sign check (
        (type in ('RECEIPT', 'RETURN') and quantity > 0) or
        (type = 'ISSUE' and quantity < 0 and project_id is not null) or
        (type = 'ADJUSTMENT'))
);
create index ix_movements_material on inventory_movements (material_id, occurred_at desc);
create index ix_movements_project on inventory_movements (project_id);
