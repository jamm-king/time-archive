create table purchase_reservations (
    id uuid primary key,
    buyer_id uuid not null,
    start_second bigint not null,
    end_second bigint not null,
    amount_cents bigint not null,
    currency varchar(3) not null,
    status varchar(32) not null,
    expires_at timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint purchase_reservations_valid_range check (
        start_second >= 0
        and end_second > start_second
        and end_second <= 86400
    ),
    constraint purchase_reservations_positive_amount check (
        amount_cents > 0
    ),
    constraint purchase_reservations_usd_currency check (
        currency = 'USD'
    ),
    constraint purchase_reservations_valid_status check (
        status in ('HELD', 'CHECKOUT_CREATED', 'COMPLETED', 'EXPIRED', 'CANCELLED')
    )
);

alter table purchase_reservations
    add constraint purchase_reservations_no_active_overlap
    exclude using gist (
        int8range(start_second, end_second, '[)') with &&
    )
    where (status in ('HELD', 'CHECKOUT_CREATED'));

create index purchase_reservations_buyer_id_idx
    on purchase_reservations (buyer_id);

create index purchase_reservations_active_range_idx
    on purchase_reservations using gist (int8range(start_second, end_second, '[)'))
    where status in ('HELD', 'CHECKOUT_CREATED');

create index purchase_reservations_expires_at_idx
    on purchase_reservations (expires_at)
    where status in ('HELD', 'CHECKOUT_CREATED');
