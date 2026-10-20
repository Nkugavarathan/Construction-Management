create table suppliers (
    id               uuid primary key,
    organization_id  uuid         not null references organizations (id),
    name             varchar(160) not null,
    contact_name     varchar(160),
    email            varchar(254),
    phone            varchar(40),
    address          varchar(300),
    tax_id           varchar(40),
    active           boolean      not null default true,
    version          bigint       not null default 0,
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    constraint uq_supplier_name unique (organization_id, name)
);

create table purchase_orders (
    id               uuid primary key,
    organization_id  uuid          not null references organizations (id),
    number           varchar(20)   not null,
    supplier_id      uuid          not null references suppliers (id),
    project_id       uuid references projects (id),
    status           varchar(24)   not null check (status in ('DRAFT', 'SUBMITTED', 'APPROVED', 'PARTIALLY_RECEIVED',
                                                              'RECEIVED', 'CANCELLED', 'CLOSED')),
    expected_date    date,
    notes            varchar(1000),
    status_reason    varchar(500),
    total_amount     numeric(15,2) not null default 0 check (total_amount >= 0),
    created_by       uuid          not null references users (id),
    submitted_by     uuid references users (id),
    approved_by      uuid references users (id),
    approved_at      timestamptz,
    version          bigint        not null default 0,
    created_at       timestamptz   not null default now(),
    updated_at       timestamptz   not null default now(),
    constraint uq_po_number unique (organization_id, number),
    constraint ck_po_sod check (approved_by is null or approved_by <> created_by)
);
create index ix_po_org_status on purchase_orders (organization_id, status);

create table purchase_order_items (
    id                 uuid primary key,
    purchase_order_id  uuid          not null references purchase_orders (id),
    material_id        uuid          not null references materials (id),
    quantity_ordered   numeric(14,3) not null check (quantity_ordered > 0),
    quantity_received  numeric(14,3) not null default 0 check (quantity_received >= 0),
    unit_price         numeric(15,4) not null check (unit_price >= 0),
    line_total         numeric(15,2) not null check (line_total >= 0),
    constraint ck_po_item_received check (quantity_received <= quantity_ordered),
    constraint uq_po_item_material unique (purchase_order_id, material_id)
);

create table goods_receipts (
    id                 uuid primary key,
    organization_id    uuid         not null references organizations (id),
    purchase_order_id  uuid         not null references purchase_orders (id),
    delivery_note_ref  varchar(80)  not null,
    idempotency_key    varchar(80)  not null,
    request_hash       varchar(64)  not null,
    received_by        uuid         not null references users (id),
    received_at        timestamptz  not null default now(),
    notes              varchar(1000),
    -- A retried request (same key) returns the original receipt instead of booking stock twice.
    constraint uq_receipt_idempotency unique (organization_id, idempotency_key),
    -- The same paper delivery note cannot be booked twice against one PO.
    constraint uq_receipt_delivery_note unique (purchase_order_id, delivery_note_ref)
);

create table goods_receipt_items (
    id                      uuid primary key,
    goods_receipt_id        uuid          not null references goods_receipts (id),
    purchase_order_item_id  uuid          not null references purchase_order_items (id),
    quantity                numeric(14,3) not null check (quantity > 0)
);
