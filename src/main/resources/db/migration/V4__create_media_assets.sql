create table media_assets (
    id uuid primary key,
    ownership_record_id uuid not null,
    owner_id uuid not null,
    media_type varchar(32) not null,
    original_file_url text not null,
    approved_file_url text null,
    thumbnail_url text null,
    external_link text null,
    moderation_status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint media_assets_valid_media_type check (
        media_type in ('IMAGE', 'VIDEO')
    ),
    constraint media_assets_valid_moderation_status check (
        moderation_status in (
            'UPLOADED',
            'PENDING_REVIEW',
            'APPROVED',
            'REJECTED',
            'HIDDEN',
            'DELETED_BY_OWNER'
        )
    ),
    constraint media_assets_original_file_url_not_blank check (
        length(btrim(original_file_url)) > 0
    ),
    constraint media_assets_approved_file_url_not_blank check (
        approved_file_url is null or length(btrim(approved_file_url)) > 0
    ),
    constraint media_assets_thumbnail_url_not_blank check (
        thumbnail_url is null or length(btrim(thumbnail_url)) > 0
    ),
    constraint media_assets_external_link_not_blank check (
        external_link is null or length(btrim(external_link)) > 0
    ),
    constraint media_assets_approved_requires_approved_file_url check (
        moderation_status <> 'APPROVED' or approved_file_url is not null
    ),
    constraint media_assets_valid_timestamps check (
        updated_at >= created_at
    )
);

create index media_assets_ownership_record_id_idx
    on media_assets (ownership_record_id);

create index media_assets_owner_id_idx
    on media_assets (owner_id);

create index media_assets_approved_ownership_record_id_idx
    on media_assets (ownership_record_id)
    where moderation_status = 'APPROVED';
