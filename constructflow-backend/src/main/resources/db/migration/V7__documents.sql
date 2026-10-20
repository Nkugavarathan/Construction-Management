create table documents (
    id                  uuid primary key,
    organization_id     uuid         not null references organizations (id),
    project_id          uuid         not null references projects (id),
    title               varchar(200) not null,
    category            varchar(32)  not null check (category in ('DRAWING', 'CONTRACT', 'PERMIT', 'SPECIFICATION',
                                                                  'REPORT', 'PHOTO', 'INVOICE', 'OTHER')),
    current_version_no  integer      not null default 1,
    created_by          uuid         not null references users (id),
    version             bigint       not null default 0,
    created_at          timestamptz  not null default now(),
    updated_at          timestamptz  not null default now()
);
create index ix_documents_project on documents (project_id, category);

create table document_versions (
    id                 uuid primary key,
    document_id        uuid         not null references documents (id),
    version_no         integer      not null check (version_no > 0),
    storage_key        varchar(300) not null unique,
    original_filename  varchar(255) not null,
    content_type       varchar(100) not null,
    size_bytes         bigint       not null check (size_bytes > 0),
    sha256             varchar(64)  not null,
    change_note        varchar(500),
    uploaded_by        uuid         not null references users (id),
    uploaded_at        timestamptz  not null default now(),
    constraint uq_document_version unique (document_id, version_no)
);
