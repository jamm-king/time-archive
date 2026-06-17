create table purchases (
    id uuid primary key,
    buyer_id uuid not null,
    reservation_id uuid not null references purchase_reservations(id),
    start_second bigint not null,
    end_second bigint not null,
    amount_cents bigint not null,
    currency varchar(3) not null,
    status varchar(32) not null,
    payment_provider varchar(64) null,
    payment_reference varchar(255) null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint purchases_valid_range check (
        start_second >= 0
        and end_second > start_second
        and end_second <= 86400
    ),
    constraint purchases_positive_amount check (amount_cents > 0),
    constraint purchases_usd_currency check (currency = 'USD'),
    constraint purchases_valid_status check (
        status in ('PENDING_PAYMENT', 'PAID', 'OWNERSHIP_GRANTED', 'FAILED', 'EXPIRED', 'REFUNDED')
    ),
    constraint purchases_reservation_unique unique (reservation_id)
);

create table payment_events (
    id uuid primary key,
    provider varchar(64) not null,
    provider_event_id varchar(255) not null,
    event_type varchar(128) not null,
    payload_hash varchar(128) not null,
    processing_status varchar(32) not null,
    received_at timestamptz not null,
    processed_at timestamptz null,
    constraint payment_events_provider_event_unique unique (provider, provider_event_id),
    constraint payment_events_valid_processing_status check (
        processing_status in ('RECEIVED', 'PROCESSED', 'FAILED')
    ),
    constraint payment_events_processed_at_required check (
        (processing_status = 'PROCESSED' and processed_at is not null)
        or (processing_status <> 'PROCESSED')
    )
);

create table audit_logs (
    id uuid primary key,
    actor_user_id uuid null,
    actor_type varchar(32) not null,
    action varchar(128) not null,
    resource_type varchar(128) not null,
    resource_id uuid not null,
    before_state jsonb null,
    after_state jsonb null,
    request_id varchar(128) null,
    created_at timestamptz not null
);

create table outbox_events (
    id uuid primary key,
    event_type varchar(128) not null,
    aggregate_type varchar(128) not null,
    aggregate_id uuid not null,
    payload jsonb not null,
    status varchar(32) not null,
    created_at timestamptz not null,
    processed_at timestamptz null,
    retry_count integer not null default 0,
    last_error text null,
    constraint outbox_events_valid_status check (
        status in ('PENDING', 'PROCESSED', 'FAILED')
    ),
    constraint outbox_events_non_negative_retry_count check (
        retry_count >= 0
    )
);

create index purchases_buyer_id_idx on purchases (buyer_id);
create index payment_events_provider_event_idx on payment_events (provider, provider_event_id);
create index audit_logs_resource_idx on audit_logs (resource_type, resource_id);
create index outbox_events_pending_idx on outbox_events (status, created_at)
    where status = 'PENDING';
