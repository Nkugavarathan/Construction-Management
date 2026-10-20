-- Tenants, global user identities, memberships (user <-> organization with a role) and revocable sessions.

create table organizations (
    id                          uuid primary key,
    name                        varchar(160) not null,
    slug                        varchar(80)  not null unique,
    currency_code               varchar(3)   not null default 'LKR' check (currency_code ~ '^[A-Z]{3}$'),
    timezone                    varchar(64)  not null default 'Asia/Colombo',
    expense_approval_threshold  numeric(15,2) not null default 100000.00 check (expense_approval_threshold >= 0),
    version                     bigint       not null default 0,
    created_at                  timestamptz  not null default now(),
    updated_at                  timestamptz  not null default now()
);

create table users (
    id                    uuid primary key,
    email                 varchar(254) not null unique check (email = lower(email)),
    password_hash         varchar(255) not null,
    full_name             varchar(160) not null,
    must_change_password  boolean      not null default false,
    failed_login_count    integer      not null default 0 check (failed_login_count >= 0),
    locked_until          timestamptz,
    last_login_at         timestamptz,
    created_at            timestamptz  not null default now(),
    updated_at            timestamptz  not null default now()
);

create table organization_memberships (
    id               uuid primary key,
    organization_id  uuid        not null references organizations (id),
    user_id          uuid        not null references users (id),
    role             varchar(32) not null check (role in ('ADMIN', 'PROJECT_MANAGER', 'SITE_ENGINEER',
                                                         'FINANCE_OFFICER', 'STOREKEEPER', 'VIEWER')),
    status           varchar(16) not null default 'ACTIVE' check (status in ('ACTIVE', 'DISABLED')),
    version          bigint      not null default 0,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    constraint uq_membership_org_user unique (organization_id, user_id)
);
create index ix_membership_user on organization_memberships (user_id);

-- Refresh tokens are stored hashed. A "family" is one login session; rotation keeps the family,
-- reuse of a rotated token revokes the whole family.
create table refresh_tokens (
    id                      uuid primary key,
    user_id                 uuid         not null references users (id),
    token_hash              varchar(64)  not null unique,
    family_id               uuid         not null,
    active_organization_id  uuid         not null references organizations (id),
    expires_at              timestamptz  not null,
    family_expires_at       timestamptz  not null,
    revoked_at              timestamptz,
    replaced_by_id          uuid references refresh_tokens (id),
    replaced_at             timestamptz,
    user_agent              varchar(255),
    ip_address              varchar(64),
    created_at              timestamptz  not null default now()
);
create index ix_refresh_tokens_family on refresh_tokens (family_id);
create index ix_refresh_tokens_user on refresh_tokens (user_id);

-- Human-readable per-organization numbering (EXP-000001, PO-000001).
create table org_sequences (
    organization_id  uuid        not null references organizations (id),
    name             varchar(32) not null,
    next_value       bigint      not null default 1,
    primary key (organization_id, name)
);
