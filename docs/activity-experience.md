# Activity experience v1

Auth is the owner of the immutable v1 XP policy, ledger, aggregate and `users.grade`.
The grade column is excluded from ORM updates: only the locked experience transaction,
first-verification initialization and guarded freeze write it explicitly. This prevents
stale detached OAuth/login saves and managed profile/status writes from undoing promotion.
Summary enqueue reads the grade with a current `FOR UPDATE` read after locking the user,
so a repeatable-read snapshot or detached merge cannot publish an obsolete rank.
`role` and JWT permissions are unchanged. The Community producer contract is
`contracts/user-activity.schema.json` in devnogi-community-server. Each activity
state is atomic across both beneficiaries of a like. The consumer preserves zero
balance tombstones, accepts only increasing source revisions, and locks user IDs
in ascending order before changing the ledger or summary outbox.

## Runtime and APIs

- `EXPERIENCE_CONSUMER_ENABLED=false` by default; enable only after freeze.
- `EXPERIENCE_TOPIC=USER_ACTIVITY_EVENT`, `EXPERIENCE_GROUP_ID=devnogi-auth-experience-v1`.
- New consumer groups start at `earliest` so the rollout backlog is retained.
- Retriable failures retry five times at one second intervals. Unrecoverable
  records go to `USER_ACTIVITY_EVENT.DLT` with original payload and Kafka headers.
  Failed DLT publishing is not acknowledged. Provision source and DLT retention
  before capture. Re-publish repaired DLT payloads to the source topic; keep the
  original event ID/activity key/revision for transient-failure retries. Do not
  reset group offsets or edit ledger rows to repair an incident.
- `GET /api/user/experience` returns this user's current XP/progress/thresholds.
- `GET /api/user/experience/history?cursor=<id>&size=20` returns descending
  immutable history IDs, at most 100 rows. Zero-delta state changes remain
  auditable. Both endpoints require the current active user's authenticated ID.
- Public DTOs and `USER_INFO_UPDATE_EVENT` add nullable `activityGrade`; hide it
  whenever the user is not currently verified and active.

## Staged rollout (requires the normal production approval)

1. Deploy additive migrations and upgraded Auth verification writers on every
   instance, with the experience consumer disabled. `experience_valid_from` and
   `valid_until` are UTC wall-clock values; legacy `verified_at` remains untouched.
   New XP timestamps use JDBC 4 `LocalDateTime` reads/binds (including explicit
   Hibernate mappings for the two interval columns), avoiding `Timestamp` conversion
   through the JVM timezone. MySQL tests force a UTC connection/session with an
   Asia/Seoul JVM and compare T0 and processing times against independent instants.
   Baseline rows have `experience_baseline=true` and are excluded from the
   existing personal/public verification history; verification count is unchanged.
2. Deploy Community's compatible author projection and durable activity capture,
   confirm capture on every instance, and drain old-version requests.
3. Run the operator with the same approved DB configuration as Auth, using a
   separate process and disabled listeners/outbox worker. Dry-run first:

   ```text
   java -jar auth.jar --server.port=0 --spring.kafka.listener.auto-startup=false --app.search.warmup-enabled=false --app.experience.consumer-enabled=false --app.experience.operator-enabled=true --app.experience.operator-action=dry-run
   ```

   Replace `dry-run` with `freeze` and add
   `--app.experience.operator-confirm=FREEZE_XP_V1` to freeze. The operator locks
   the policy and users, saves T0 once, freezes the verified ACTIVE cohort, and
   refreshes the UTC interval baseline to cover changes by old Auth instances
   during rollout. It initializes missing grades and emits a fresh grade-bearing
   summary revision for every cohort member, including members with no activity;
   an old publisher may have acknowledged an earlier snapshot without the new field.
   Repeating freeze returns the same T0 and does not change the
   cohort. Record its UTC T0 in Community's guarded backfill command and freeze
   its source cutoffs; do not enable another independent cohort later.
4. Enable the consumer and Community backfill. All captured events before T0 and
   revision-zero snapshots use the frozen cohort. Later events use UTC verification
   intervals. Reversals are applied even after verification loss. Source revisions
   protect against late backfill, redelivery and reversed delivery order.
5. Run the same operator with `--app.experience.operator-action=reconcile`.
   `ledger_mismatches` and `grade_mismatches` must both be zero; otherwise the
   operator fails with a nonzero process exit. Compare Community
   backfill completion/checkpoints and outbox lag before exposing the UI. `last_backfill_at`
   is a per-user processing timestamp, not proof that all Community sources finished.

Operator mode disables scheduled jobs, and exits after completing its one action. Do not run it inside the
long-lived production service. To pause delivery, disable the Auth consumer or the
Community publisher through the approved deployment procedure. Once capture has
started, keep Community capture enabled so paused activity is retained; preserve
offsets, outbox and ledger data.
No startup task automatically freezes the cohort or replays Community data.

## Verification

`gradlew.bat verify --no-daemon` runs regular tests/format/PMD/bootJar. The actual
MySQL suite is opt-in and requires a **dedicated disposable MySQL instance** with
schema `auth`, because a historical immutable migration explicitly inserts into
`auth.roles`. Never point these tests at an application database containing real data.

```text
EXPERIENCE_MYSQL_REQUIRED=true
EXPERIENCE_MYSQL_URL=jdbc:mysql://127.0.0.1:33377/auth?useSSL=false&allowPublicKeyRetrieval=true
EXPERIENCE_MYSQL_USER=root
EXPERIENCE_MYSQL_PASSWORD=<disposable password>
gradlew.bat test --tests *ExperienceMySqlTest --no-daemon
```

The suite runs under Asia/Seoul (the application's default zone) while testing
UTC event/interval equivalence, duplicate/reversed events, concurrent updates,
two-sided likes, reversal/demotion, frozen cohort, atomic rollback, private
cursor pagination and hidden verification baselines. CI provisions a dedicated
MySQL service for this suite. Cross-service Kafka redelivery and rendered UI
evidence are tracked in the workspace feature review.

## Advertising consumption (Hero v1)

`user_experience.total_xp` and the activity history keep their activity-only meaning.
`hero_charge` is an immutable signed debit/refund ledger; `hero_reservation` contains
only active highest-bid holds. `ExperienceWalletService` derives current XP as
`max(0, activity - net spending)`, available XP after holds, and a persisted-by-ledger
deficit when activity is reversed after spending. New earnings first repay that
deficit. Holds do not change grade; debit/refund and subsequent activity changes do.

Summary JSON preserves `totalXp` and adds `currentXp`, `spentXp`, `reservedXp`,
`availableXp`, `deficitXp`. Grade and progression are based on current XP. Advertising
transactions are separately available in `/api/user/hero/auctions`; activity history
does not mix payment entries into activity policy rows. Community's existing summary
outbox contract is unchanged. Reconciliation now checks consumption-adjusted grade,
negative net spending, reservation versus leading bid, and payment/refund correspondence.
Any mismatch fails the reconciliation operator command before activation.

See [approved plan](hero-plan.md) and [HTTP contract](hero-api.md). CI also executes
`HeroMySqlTest` against the same dedicated MySQL service, sequentially with the XP
suite. No real account, production storage or production database is used in tests.
