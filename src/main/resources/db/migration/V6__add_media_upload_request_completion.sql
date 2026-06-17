alter table media_upload_requests
    add column media_asset_id uuid null references media_assets (id);

alter table media_upload_requests
    add constraint media_upload_requests_completed_media_asset_check
        check (
            (status = 'COMPLETED' and media_asset_id is not null)
            or (status <> 'COMPLETED' and media_asset_id is null)
        );
