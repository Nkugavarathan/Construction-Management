create table budget_categories (
    id               uuid primary key,
    organization_id  uuid         not null references organizations (id),
    name             varchar(80)  not null,
    active           boolean      not null default true,
    created_at       timestamptz  not null default now(),
    constraint uq_budget_category unique (organization_id, name)
);

create table budget_allocations (
    id           uuid primary key,
    project_id   uuid          not null references projects (id),
    category_id  uuid          not null references budget_categories (id),
    amount       numeric(15,2) not null check (amount >= 0),
    created_at   timestamptz   not null default now(),
    updated_at   timestamptz   not null default now(),
    constraint uq_budget_allocation unique (project_id, category_id)
);

create table expenses (
    id                   uuid primary key,
    organization_id      uuid          not null references organizations (id),
    project_id           uuid          not null references projects (id),
    category_id          uuid          not null references budget_categories (id),
    number               varchar(20)   not null,
    description          varchar(500)  not null,
    vendor_name          varchar(160),
    amount               numeric(15,2) not null check (amount <> 0),
    expense_date         date          not null,
    status               varchar(16)   not null check (status in ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    created_by           uuid          not null references users (id),
    submitted_by         uuid references users (id),
    submitted_at         timestamptz,
    decided_by           uuid references users (id),
    decided_at           timestamptz,
    reverses_expense_id  uuid unique references expenses (id),
    version              bigint        not null default 0,
    created_at           timestamptz   not null default now(),
    updated_at           timestamptz   not null default now(),
    constraint uq_expense_number unique (organization_id, number),
    -- Segregation of duties, enforced by the database as well as the service layer.
    constraint ck_expense_sod check (decided_by is null or submitted_by is null or decided_by <> submitted_by),
    -- Only reversals may carry a negative amount.
    constraint ck_expense_sign check ((reverses_expense_id is null and amount > 0) or (reverses_expense_id is not null and amount < 0))
);
create index ix_expenses_project_status on expenses (project_id, status);
create index ix_expenses_org_status on expenses (organization_id, status, submitted_at);

-- Append-only approval history.
create table expense_approvals (
    id          uuid primary key,
    expense_id  uuid         not null references expenses (id),
    action      varchar(16)  not null check (action in ('SUBMITTED', 'APPROVED', 'REJECTED')),
    actor_id    uuid         not null references users (id),
    comment     varchar(1000),
    created_at  timestamptz  not null default now()
);
create index ix_expense_approvals_expense on expense_approvals (expense_id, created_at);
