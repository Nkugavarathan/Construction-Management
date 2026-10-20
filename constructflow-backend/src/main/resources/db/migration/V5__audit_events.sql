create table audit_events (
    id               uuid primary key,
    organization_id  uuid         not null references organizations (id),
    project_id       uuid,
    actor_id         uuid references users (id),
    action           varchar(64)  not null,
    entity_type      varchar(64)  not null,
    entity_id        uuid,
    summary          varchar(500) not null,
    changes          jsonb,
    correlation_id   varchar(64),
    ip_address       varchar(64),
    created_at       timestamptz  not null default now()
);
create index ix_audit_org_created on audit_events (organization_id, created_at desc);
create index ix_audit_entity on audit_events (entity_type, entity_id);

-- Application-level immutability: no UPDATE or DELETE, regardless of what the application code does.
-- Limitation: the table owner can still drop this trigger. Tamper evidence (hash chaining or WORM
-- storage) is a documented future improvement.
create function reject_audit_modification() returns trigger as $$
begin
    raise exception 'audit_events is append-only';
end;
$$ language plpgsql;

create trigger trg_audit_events_immutable
    before update or delete on audit_events
    for each row execute function reject_audit_modification();
