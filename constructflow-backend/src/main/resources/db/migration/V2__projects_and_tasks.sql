create table projects (
    id                uuid primary key,
    organization_id   uuid          not null references organizations (id),
    code              varchar(32)   not null,
    name              varchar(160)  not null,
    description       text,
    client_name       varchar(160)  not null,
    client_contact    varchar(160),
    site_address      varchar(300)  not null,
    start_date        date          not null,
    planned_end_date  date          not null,
    actual_end_date   date,
    approved_budget   numeric(15,2) not null check (approved_budget >= 0),
    status            varchar(16)   not null check (status in ('PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'CANCELLED')),
    status_reason     varchar(500),
    manager_id        uuid          not null references users (id),
    version           bigint        not null default 0,
    created_at        timestamptz   not null default now(),
    updated_at        timestamptz   not null default now(),
    constraint uq_project_code unique (organization_id, code),
    constraint ck_project_dates check (planned_end_date >= start_date)
);
create index ix_projects_org_status on projects (organization_id, status);

create table project_memberships (
    id               uuid primary key,
    organization_id  uuid        not null references organizations (id),
    project_id       uuid        not null references projects (id),
    user_id          uuid        not null references users (id),
    project_role     varchar(16) not null check (project_role in ('MANAGER', 'MEMBER')),
    created_at       timestamptz not null default now(),
    constraint uq_project_member unique (project_id, user_id)
);
create index ix_project_members_user on project_memberships (user_id, project_id);

create table milestones (
    id            uuid primary key,
    project_id    uuid         not null references projects (id),
    name          varchar(160) not null,
    description   text,
    due_date      date         not null,
    status        varchar(16)  not null check (status in ('PENDING', 'COMPLETED')),
    completed_at  timestamptz,
    version       bigint       not null default 0,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now()
);
create index ix_milestones_project on milestones (project_id, due_date);

create table tasks (
    id            uuid primary key,
    project_id    uuid         not null references projects (id),
    milestone_id  uuid references milestones (id),
    title         varchar(200) not null,
    description   text,
    assignee_id   uuid references users (id),
    priority      varchar(16)  not null check (priority in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status        varchar(16)  not null check (status in ('TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE', 'CANCELLED')),
    status_reason varchar(500),
    weight        smallint     not null default 1 check (weight between 1 and 10),
    start_date    date,
    due_date      date,
    completed_at  timestamptz,
    created_by    uuid         not null references users (id),
    version       bigint       not null default 0,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    constraint ck_task_dates check (due_date is null or start_date is null or due_date >= start_date),
    constraint ck_task_completed check ((status = 'DONE') = (completed_at is not null))
);
create index ix_tasks_project_status on tasks (project_id, status, due_date);
create index ix_tasks_assignee on tasks (assignee_id, status);
create index ix_tasks_milestone on tasks (milestone_id);

create table site_reports (
    id               uuid primary key,
    project_id       uuid         not null references projects (id),
    author_id        uuid         not null references users (id),
    report_date      date         not null,
    weather          varchar(80),
    workforce_count  integer      not null check (workforce_count >= 0),
    work_performed   text         not null,
    issues           text,
    created_at       timestamptz  not null default now(),
    constraint uq_site_report unique (project_id, author_id, report_date)
);
create index ix_site_reports_project on site_reports (project_id, report_date desc);
