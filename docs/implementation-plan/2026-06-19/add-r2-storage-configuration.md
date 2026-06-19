# Add R2 Storage Configuration

## Objective

Prepare a safe, reproducible local configuration path for connecting the
existing S3-compatible storage adapter to Cloudflare R2.

## Scope

- Add a Docker Compose override for R2-backed local verification.
- Add a non-secret R2 environment example.
- Document how to create local secret env values without committing them.
- Document environment-specific bucket separation for local and deployed R2
  usage.
- Document the validation flow using existing media upload, admin preview, and
  public timeline scripts.

Out of scope:

- Committing real R2 access keys.
- Creating Cloudflare resources through code.
- Production deployment automation.
- Changing the storage adapter behavior unless verification shows it is
  required.

## Relevant Files Or Modules

- `docker-compose.r2.yml`
- `docs/operations/r2.env.example`
- `docs/operations/r2-storage-setup.md`
- `README.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-06-19/add-r2-storage-configuration.md`

## Key Design Decisions

- Do not commit secrets. Local credentials must live in `.env.r2`, which is
  ignored by Git.
- Use the Cloudflare R2 S3-compatible account endpoint for API and presigned
  URL generation.
- Use the custom media domain as the stored managed object reference base URL.
- Keep MinIO as the default local path; R2 is opt-in through a Compose override.
- Do not hardcode bucket or custom-domain values in the Compose override.
  Local R2 verification must provide environment-specific values through
  `.env.r2`.
- Treat `TIME_ARCHIVE_STORAGE_S3_BUCKET` and
  `TIME_ARCHIVE_STORAGE_S3_PUBLIC_BASE_URL` as data compatibility boundaries.
  Existing database rows can point to objects in a previous storage backend.

## Known R2 Values

- Account ID: intentionally not committed.
- S3 API endpoint:
  `https://replace-with-account-id.r2.cloudflarestorage.com`
- Bucket and custom-domain values are environment-specific. Local verification
  should use a separate bucket from production.

## Step-By-Step Execution Plan

- [x] Create a dedicated feature branch from latest `main`.
- [x] Add this implementation plan.
- [x] Add R2 Docker Compose override.
- [x] Add R2 env example without secrets.
- [x] Add R2 setup and verification documentation.
- [x] Link R2 setup from README and release readiness checklist.
- [x] Run YAML/config checks where possible.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: R2 CORS or custom domain settings may still need Cloudflare dashboard
  changes.
  - Mitigation: Document the required owner-side setup and validation steps.
- Risk: Compose variable interpolation can accidentally read missing secrets as
  empty values.
  - Mitigation: Use required variable interpolation for access keys.
- Risk: Public custom domain behavior may differ from the S3 API endpoint.
  - Mitigation: Use the R2 S3 API endpoint for signing and the custom domain
    only as a managed storage reference base.
- Risk: Switching storage backends or buckets while keeping the same database
  can leave media rows pointing to a previous storage environment.
  - Mitigation: Document local database reset as the preferred verification
    path and require a formal object/database migration plan for deployed
    environments.

Rollback:

- Remove the R2 override, env example, setup doc, links, and this plan.

## Verification Plan

- `docker compose --env-file .env.r2 -f docker-compose.yml -f docker-compose.r2.yml config`
- Existing verification scripts after the owner creates `.env.r2`:
  - `./scripts/verify-local-media-upload-flow.sh`
  - `./scripts/verify-local-admin-preview-flow.sh`
  - `./scripts/verify-local-public-timeline-flow.sh`
- `git diff --check`

## Open Questions

- Access key ID and secret are intentionally not collected in this thread.
- Bucket CORS must be configured in Cloudflare before browser upload is tested.

## Progress Log

- 2026-06-19: Confirmed local `main` is up to date.
- 2026-06-19: Created `feature/r2-storage-configuration`.
- 2026-06-19: Added implementation plan.
- 2026-06-19: Added the initial R2 Compose override using concrete local test
  values. This was later changed to environment-specific values from `.env.r2`
  so account, bucket, and custom-domain identifiers are not committed.
- 2026-06-19: Added non-secret R2 env example and setup documentation.
- 2026-06-19: Linked R2 setup from README and release readiness checklist.
- 2026-06-19: Validated the Compose override with temporary placeholder
  credentials.
- 2026-06-19: Discovered the running API container still had MinIO storage
  environment values because it had not been recreated with the R2 override.
- 2026-06-19: Recreated the API and web containers with
  `docker compose --env-file .env.r2 -f docker-compose.yml -f docker-compose.r2.yml up -d --build --force-recreate api web`.
- 2026-06-19: Confirmed the recreated API container uses the R2 endpoint,
  custom media base URL, bucket, and `auto` region.
- 2026-06-19: Verified a fresh R2-backed media upload script run and confirmed
  the created media asset stores an original file URL under the configured R2
  public base URL.
- 2026-06-19: Investigated buyer browser upload failures. Confirmed upload
  requests were created and left in `REQUESTED` status, then reproduced the R2
  browser preflight response: `403 Unauthorized` with `CORS not configured for
  this bucket`.
- 2026-06-19: Documented an explicit local browser-upload R2 CORS policy.
- 2026-06-19: Changed the R2 Compose override to require endpoint, presigned
  endpoint, public base URL, and bucket values from `.env.r2` so local
  verification can use a bucket separate from production.
- 2026-06-19: Updated R2 documentation and release readiness checklist to make
  bucket separation an explicit operational rule.
- 2026-06-19: Documented that storage backend, bucket, and public base URL
  changes can break database/object-storage consistency. Local verification
  should use disposable data or a clean database after switching storage, while
  deployed environments require a migration plan before any storage change.

## Completion Summary

Added an opt-in R2 configuration path for local verification. The default local
stack continues to use MinIO. R2 can be enabled with
`docker-compose.r2.yml` and a local, ignored `.env.r2` file containing R2 access
credentials.

## Files Changed

- `docker-compose.r2.yml`
- `docs/operations/r2.env.example`
- `docs/operations/r2-storage-setup.md`
- `README.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-06-19/add-r2-storage-configuration.md`

## Tests Run And Results

- `docker compose -f docker-compose.yml -f docker-compose.r2.yml config` with
  temporary placeholder credentials: passed.
- `docker compose -f docker-compose.yml -f docker-compose.r2.yml config --quiet`
  with temporary placeholder endpoint, bucket, public base URL, and credentials:
  passed after environment-specific R2 variable changes.
- Initial verification scripts with ranges `[7000, 7001)`, `[7100, 7101)`,
  `[7200, 7201)`, and `[7300, 7301)` passed, but DB inspection later showed
  they had used the pre-existing MinIO-backed API container.
- `./scripts/verify-local-media-upload-flow.sh` with range `[7400, 7401)`
  after API recreation: passed.
- `git diff --check`: passed.

## Manual Verification Results

- Confirmed the R2-backed local stack is running with
  `docker compose --env-file .env.r2 -f docker-compose.yml -f docker-compose.r2.yml ps`.
- Before environment-specific variable extraction, confirmed the recreated API
  container had R2 endpoint, presigned endpoint, public base URL, bucket, and
  `auto` region values. Concrete identifiers were removed from this committed
  plan.
- Confirmed a fresh media asset created after API recreation stores an
  original file URL under the configured R2 public base URL.
- Confirmed browser upload failure is currently caused by missing R2 bucket
  CORS configuration, not by the API upload request creation step.

## Known Limitations

- Full interactive browser upload verification after API recreation should be
  rerun by the project owner after applying the R2 bucket CORS policy.
- Admin preview, public timeline, and web-origin purchase-upload scripts should
  be rerun after API recreation before committing this setup branch.
- The base Compose stack still starts MinIO, although the API uses R2 when the
  override is applied.

## Follow-Up Recommendations

- Create `.env.r2` locally from `docs/operations/r2.env.example`.
- Run the R2-backed media upload, admin preview, public timeline, and web upload
  verification scripts.
- Configure equivalent secrets in the eventual deployment environment through
  secret management, not committed files.
- For future production storage migrations, consider replacing persisted full
  storage URLs with a model based on object keys plus an explicit storage
  profile.
