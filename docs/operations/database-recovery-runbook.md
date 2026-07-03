# Database Recovery Runbook

## Purpose

This runbook defines the Time Archive MVP database backup and recovery policy.
It covers RDS automated backups, point-in-time recovery, final snapshots,
restore drills, Flyway verification, and the consistency boundary between RDS
rows and Cloudflare R2 objects.

This document is an operating policy. It does not prove that production RDS
backups are enabled or that a restore drill has already passed.

## Recovery Goals

Initial paid-production goals:

| Goal | MVP target |
| --- | --- |
| Backup mechanism | RDS automated backups with point-in-time recovery |
| Backup retention | 7 days minimum for paid production |
| Final snapshot | Required before intentional production DB deletion |
| Restore drill | Required before public paid launch |
| RPO target | Less than 24 hours, measured during restore drill |
| RTO target | Less than 4 hours for first MVP, measured during restore drill |
| Normal migration rollback | Forward-fix migration |
| Severe data recovery | Point-in-time restore into a new isolated database |

The RPO/RTO values are initial operational targets, not contractual uptime or
data-loss commitments.

## Backup Policy

Production RDS must be configured with:

- automated backups enabled;
- backup retention period of at least 7 days;
- storage encryption enabled;
- deletion protection enabled;
- final snapshot required for intentional deletion;
- PostgreSQL log export to CloudWatch;
- private networking with no public RDS access.

Staging may use shorter backup retention to control cost, but staging restore
drills should still exercise the same restore procedure shape that production
will use.

## When To Use Each Recovery Path

| Situation | Preferred recovery |
| --- | --- |
| Failed application deploy before DB migration | Redeploy previous image SHA |
| Failed Flyway migration before app replacement | Stop deploy and forward-fix migration |
| Compatible migration but app bug after deploy | Roll forward or redeploy previous image, depending on schema compatibility |
| Bad data from application bug | Stop writes, assess blast radius, prefer corrective SQL or application repair |
| Severe data corruption | Restore RDS to a new instance at the selected recovery point |
| Accidental destructive SQL | Restore to a new instance, compare, and selectively recover or switch over |
| RDS instance failure | Restore or replace through RDS mechanisms; application host is replaceable |
| Lost R2 object only | Use media lifecycle or incident procedure; RDS PITR alone does not restore R2 |

Point-in-time restore is a high-impact action because it can discard legitimate
writes after the selected recovery point. It requires explicit approval.

## Pre-Launch Restore Drill

Before paid production launch, perform one restore drill in staging or an
isolated recovery environment.

Minimum drill steps:

1. Record the source DB instance identifier, engine version, parameter group,
   subnet group, security group, and backup retention configuration.
2. Capture the current application Git SHA and Flyway schema version.
3. Create a known marker record in staging, or identify a safe existing marker.
4. Wait until the marker is included in an automated backup recovery window.
5. Restore the database to a new isolated DB instance from a selected recovery
   point.
6. Confirm the restored DB is private and reachable only from an approved
   verification host or security group.
7. Run Flyway validation against the restored DB using the immutable API image
   or the same Flyway version used by deployment.
8. Run read-only smoke queries for ownership records, users, media assets,
   audit logs, and Flyway schema history.
9. Measure elapsed restore time and document RTO.
10. Compare the selected restore point with expected marker visibility and
    document observed RPO.
11. Destroy the restored DB instance after preserving the drill record, unless
    it is needed for investigation.

The drill must not restore production user data into staging unless an approved
anonymization process exists.

## Production Recovery Procedure

For a suspected data incident:

1. Stop or reduce writes if continuing traffic can worsen corruption.
2. Preserve request IDs, deployment IDs, image SHAs, migration logs, CloudWatch
   logs, and relevant audit records.
3. Identify the incident start time and candidate restore point.
4. Decide whether a forward-fix, corrective SQL, selective restore, or full
   database restore is appropriate.
5. If using PITR, restore to a new DB instance first. Do not overwrite the
   current production database in place.
6. Validate Flyway schema history and application compatibility.
7. Check R2 object references for affected media rows.
8. Plan cutover or selective recovery.
9. Execute only after explicit operator approval.
10. Record the recovery action, actor, timestamps, affected records, and
    rollback limitations.

## Flyway Verification

Every restore drill must verify:

- `flyway_schema_history` exists and is readable;
- all applied migrations are in a successful state;
- the restored schema is compatible with the target application image;
- pending migrations are understood before any application writes resume.

Do not run destructive repair or clean operations against production without a
separate approved recovery plan.

## R2 Consistency Boundary

RDS stores object references for media assets and upload requests. R2 stores
the actual object bytes. They are not restored by one operation.

Important consequences:

- Restoring RDS to an earlier point can reintroduce references to R2 objects
  deleted after that point.
- Restoring RDS to an earlier point can remove database rows for R2 objects
  still present in the bucket.
- Deleting R2 objects without updating database state creates broken media
  references.
- Public timeline playback uses presigned URLs generated from approved DB
  references, so broken references can surface as playback failures.

Before using a restored DB for production traffic, sample media references and
verify the corresponding R2 objects exist. If object drift is found, prefer
holding affected media out of approval/public playback until repaired.

## Approval Boundary

The following actions require explicit approval before execution:

- changing production backup retention;
- disabling deletion protection;
- deleting a production DB instance;
- deleting a final snapshot;
- running production PITR restore;
- switching production application traffic to a restored DB;
- running corrective SQL against production ownership, purchase, payment,
  audit, or media tables;
- deleting production R2 objects referenced by database rows.

Approval must include reason, impact, selected recovery point, rollback
limitations, and expected user-visible effect.

## Evidence To Keep

For every restore drill or real recovery, keep:

- date and actor;
- source DB identifier;
- restored DB identifier;
- source application image SHA;
- Flyway schema version before and after;
- selected recovery point;
- measured RTO;
- observed RPO;
- validation queries or smoke checks performed;
- R2 consistency checks performed;
- cleanup status for restored resources.

## Release Gate

The database recovery gate is ready for paid production only when:

- production RDS automated backups are enabled with at least 7 days retention;
- production deletion protection and final snapshot behavior are configured;
- at least one staging or isolated restore drill has passed;
- Flyway validation has passed against the restored DB;
- measured RTO and RPO are documented;
- R2 consistency checks are included in the restore drill record.

Until then, the runbook is ready but production recovery remains unverified.
