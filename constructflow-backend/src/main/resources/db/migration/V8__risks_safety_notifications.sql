create table risks (
    id           uuid primary key,
    project_id   uuid          not null references projects (id),
    title        varchar(200)  not null,
    description  text,
    likelihood   smallint      not null check (likelihood between 1 and 5),
    impact       smallint      not null check (impact between 1 and 5),
    -- Documented formula: score = likelihood x impact (1..25). Enforced here and in Risk.
    score        smallint      not null,
    mitigation   text,
    owner_id     uuid references users (id),
    due_date     date,
    status       varchar(16)   not null check (status in ('OPEN', 'MITIGATING', 'CLOSED')),
    created_by   uuid          not null references users (id),
    version      bigint        not null default 0,
    created_at   timestamptz   not null default now(),
    updated_at   timestamptz   not null default now(),
    constraint ck_risk_score check (score = likelihood * impact)
);
create index ix_risks_project on risks (project_id, status);

create table safety_incidents (
    id                  uuid primary key,
    project_id          uuid         not null references projects (id),
    occurred_at         timestamptz  not null,
    location            varchar(200) not null,
    severity            varchar(16)  not null check (severity in ('NEAR_MISS', 'MINOR', 'MAJOR', 'CRITICAL')),
    description         text         not null,
    corrective_actions  text,
    status              varchar(16)  not null check (status in ('OPEN', 'INVESTIGATING', 'CLOSED')),
    reported_by         uuid         not null references users (id),
    closed_by           uuid references users (id),
    closed_at           timestamptz,
    version             bigint       not null default 0,
    created_at          timestamptz  not null default now(),
    updated_at          timestamptz  not null default now()
);
create index ix_incidents_project on safety_incidents (project_id, occurred_at desc);

create table notifications (
    id               uuid primary key,
    organization_id  uuid         not null references organizations (id),
    recipient_id     uuid         not null references users (id),
    type             varchar(40)  not null,
    title            varchar(200) not null,
    message          varchar(500),
    link             varchar(300),
    -- Prevents the daily deadline job from notifying about the same thing twice.
    dedupe_key       varchar(200),
    read_at          timestamptz,
    created_at       timestamptz  not null default now(),
    constraint uq_notification_dedupe unique (recipient_id, dedupe_key)
);
create index ix_notifications_recipient on notifications (recipient_id, organization_id, read_at, created_at desc);
