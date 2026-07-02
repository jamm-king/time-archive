alter table media_assets
    add column duration_ms bigint null;

alter table media_assets
    add constraint media_assets_duration_ms_positive check (
        duration_ms is null or duration_ms > 0
    );
