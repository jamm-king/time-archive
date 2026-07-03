# Storage Backend Change Procedure

## Purpose

This runbook defines the procedure for changing Time Archive storage backend,
bucket, or managed object-reference base URL in a deployed environment.

Storage changes are high-impact because database rows store object references
that must remain consistent with object storage.

## Compatibility Boundary

The following values are data compatibility boundaries:

```text
TIME_ARCHIVE_STORAGE_S3_BUCKET
TIME_ARCHIVE_STORAGE_S3_PUBLIC_BASE_URL
TIME_ARCHIVE_STORAGE_S3_ENDPOINT
TIME_ARCHIVE_STORAGE_S3_PRESIGNED_URL_ENDPOINT
```

Database fields storing managed object references include:

- `media_upload_requests.original_file_url`
- `media_assets.original_file_url`
- `media_assets.approved_file_url`
- `media_assets.thumbnail_url`

The storage adapter treats URLs under the configured
`TIME_ARCHIVE_STORAGE_S3_PUBLIC_BASE_URL` as managed references. Rows created
under a different base URL can fail admin preview, approval, and public
playback after a storage change.

## Forbidden Shortcut

Do not change production bucket, endpoint, or object-reference base URL by only
editing SSM parameters.

That shortcut can create:

- database rows pointing to objects in an old bucket;
- R2 objects with no database references;
- approved media that can no longer be presigned;
- admin preview failures;
- rollback ambiguity if new writes occur after the change.

## Required Migration Plan

Before changing production storage settings, write an approved migration plan
covering:

1. Reason for the change.
2. Affected environment.
3. Current bucket, endpoint, and object-reference base URL.
4. Target bucket, endpoint, and object-reference base URL.
5. Inventory query for affected database rows.
6. Object copy or move procedure.
7. Database update procedure.
8. Verification queries and object `HEAD` checks.
9. Public timeline playback checks.
10. Admin preview checks.
11. Freeze window for uploads and moderation.
12. Rollback strategy.
13. Owner and approval record.

## Migration Execution Shape

Recommended production sequence:

1. Pause new media uploads and admin approvals.
2. Record deployment SHA, RDS snapshot or restore point, and R2 inventory.
3. Copy objects to the target bucket or prefix.
4. Verify copied object counts, sizes, and representative checksums where
   available.
5. Update database object references in bounded batches.
6. Run admin preview checks on sampled media.
7. Run public playback checks on sampled approved media.
8. Resume uploads and approvals only after verification passes.
9. Keep source objects until the rollback window closes.

## Rollback

Rollback must define whether to:

- revert SSM parameters only;
- restore database rows from a recorded migration backup;
- copy new objects back to the previous bucket;
- hide affected media temporarily;
- use point-in-time restore for severe corruption.

Point-in-time restore is high impact and can discard legitimate writes after
the selected recovery point. Prefer reversible copy and database update steps.

## Local And Staging

Local data can be reset when switching between MinIO and R2. Staging can use
the same procedure shape as production, but staging data is synthetic and may
be reset after preserving release evidence.

Do not use a successful local or staging storage switch as proof that a
production storage change is safe without a production-specific migration plan.

## Release Gate

Storage backend changes are ready only when this procedure is accepted and the
production runtime process treats bucket, endpoint, and object-reference base
URL changes as high-impact operational changes requiring explicit approval.
