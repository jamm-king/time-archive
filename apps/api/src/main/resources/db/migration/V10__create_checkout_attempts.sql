create table checkout_attempts (
    id uuid primary key,
    reservation_id uuid not null references purchase_reservations(id),
    buyer_id uuid not null,
    provider varchar(64) not null,
    provider_request_id varchar(64) not null,
    provider_reference varchar(255) null,
    checkout_url text null,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint checkout_attempts_reservation_unique unique (reservation_id),
    constraint checkout_attempts_provider_request_unique unique (provider, provider_request_id),
    constraint checkout_attempts_valid_status check (
        status in ('PENDING_PROVIDER', 'PROVIDER_CREATED', 'PROVIDER_FAILED', 'CANCELLED')
    ),
    constraint checkout_attempts_provider_created_fields check (
        (
            status = 'PROVIDER_CREATED'
            and provider_reference is not null
            and checkout_url is not null
        )
        or status <> 'PROVIDER_CREATED'
    )
);

create index checkout_attempts_buyer_id_idx
    on checkout_attempts (buyer_id);

create index checkout_attempts_status_idx
    on checkout_attempts (status);
