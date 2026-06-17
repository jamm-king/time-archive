create table media_upload_requests (
    id uuid primary key,
    ownership_record_id uuid not null references ownership_records (id),
    owner_id uuid not null,
    media_type varchar(20) not null,
    original_filename varchar(255) not null,
    content_type varchar(100) not null,
    content_length_bytes bigint not null,
    object_key varchar(500) not null,
    original_file_url text not null,
    status varchar(30) not null,
    expires_at timestamp not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint media_upload_requests_media_type_check
        check (media_type in ('IMAGE', 'VIDEO')),
    constraint media_upload_requests_status_check
        check (status in ('REQUESTED', 'COMPLETED', 'EXPIRED')),
    constraint media_upload_requests_original_filename_not_blank_check
        check (length(trim(original_filename)) > 0),
    constraint media_upload_requests_content_type_not_blank_check
        check (length(trim(content_type)) > 0),
    constraint media_upload_requests_content_length_positive_check
        check (content_length_bytes > 0),
    constraint media_upload_requests_object_key_not_blank_check
        check (length(trim(object_key)) > 0),
    constraint media_upload_requests_original_file_url_not_blank_check
        check (length(trim(original_file_url)) > 0),
    constraint media_upload_requests_expires_after_created_check
        check (expires_at > created_at),
    constraint media_upload_requests_updated_after_created_check
        check (updated_at >= created_at),
    constraint media_upload_requests_object_key_unique unique (object_key)
);

create index media_upload_requests_ownership_record_id_idx
    on media_upload_requests (ownership_record_id);

create index media_upload_requests_owner_id_idx
    on media_upload_requests (owner_id);

create index media_upload_requests_status_expires_at_idx
    on media_upload_requests (status, expires_at);
