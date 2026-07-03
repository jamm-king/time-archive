alter table checkout_attempts
    drop constraint checkout_attempts_valid_status;

alter table checkout_attempts
    drop constraint checkout_attempts_provider_created_fields;

alter table checkout_attempts
    add column capture_request_id varchar(80) null,
    add column capture_reference varchar(255) null,
    add column captured_at timestamptz null;

create index checkout_attempts_provider_reference_idx
    on checkout_attempts (provider, provider_reference)
    where provider_reference is not null;

alter table checkout_attempts
    add constraint checkout_attempts_valid_status check (
        status in (
            'PENDING_PROVIDER',
            'PROVIDER_CREATED',
            'PROVIDER_FAILED',
            'CANCELLED',
            'CAPTURED_PENDING_WEBHOOK',
            'CAPTURE_FAILED'
        )
    );

alter table checkout_attempts
    add constraint checkout_attempts_provider_created_fields check (
        (
            status in ('PROVIDER_CREATED', 'CAPTURED_PENDING_WEBHOOK', 'CAPTURE_FAILED')
            and provider_reference is not null
            and checkout_url is not null
        )
        or status not in ('PROVIDER_CREATED', 'CAPTURED_PENDING_WEBHOOK', 'CAPTURE_FAILED')
    );

alter table checkout_attempts
    add constraint checkout_attempts_captured_fields check (
        (
            status = 'CAPTURED_PENDING_WEBHOOK'
            and capture_request_id is not null
            and capture_reference is not null
            and captured_at is not null
        )
        or status <> 'CAPTURED_PENDING_WEBHOOK'
    );
