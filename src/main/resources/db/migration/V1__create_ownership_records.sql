create table ownership_records (
    id uuid primary key,
    start_second bigint not null,
    end_second bigint not null,
    owner_id uuid not null,
    status varchar(32) not null,
    valid_from timestamptz not null,
    valid_until timestamptz null,
    acquisition_type varchar(32) not null,
    source_purchase_id uuid null,
    source_transaction_id uuid null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ownership_records_valid_range check (
        start_second >= 0
        and end_second > start_second
        and end_second <= 86400
    ),
    constraint ownership_records_valid_status check (
        status in ('ACTIVE', 'TRANSFERRED', 'REVOKED')
    ),
    constraint ownership_records_valid_acquisition_type check (
        acquisition_type in ('PRIMARY_PURCHASE', 'RESALE', 'ADMIN_GRANT')
    ),
    constraint ownership_records_active_valid_until check (
        (status = 'ACTIVE' and valid_until is null)
        or (status <> 'ACTIVE' and valid_until is not null)
    )
);

alter table ownership_records
    add constraint ownership_records_no_active_overlap
    exclude using gist (
        int8range(start_second, end_second, '[)') with &&
    )
    where (status = 'ACTIVE' and valid_until is null);

create index ownership_records_owner_id_idx
    on ownership_records (owner_id);

create index ownership_records_active_range_idx
    on ownership_records using gist (int8range(start_second, end_second, '[)'))
    where status = 'ACTIVE' and valid_until is null;
