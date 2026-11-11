# Author arcana contract — revision 1

Auth owns the nullable public game-profile `arcana`. Version 1 adds that field to
the existing revisioned `USER_INFO_UPDATE_EVENT`, durable snapshot and Community
projection. Codes are the existing `Arcana` enum; no values are inferred from
verification, rank or role. XP and permission policies do not change.

New producers always send `arcana`, including explicit null when a profile is
cleared. Community preserves its value when a legacy event omits the field;
explicit null clears it. The existing per-user revision rule also covers arcana.
Profile updates already lock users before replacing profiles. Snapshot enqueue
flushes and reads the current profile under that same user lock. SQL snapshot
writers (missing-user backfill and XP rollout freeze) include arcana too.

## Deployment and post-drain replay

Deploy Community's additive column/consumer first, then upgraded Auth on every
instance. The Auth migration queues existing outboxes but deliberately leaves
`arcana_refreshed=false`: an old publisher could have acknowledged a revision
without the new field. After all old Auth publishers/writers have stopped, run
the dedicated command against the approved runtime configuration. It starts only
datasource/transactions, with no Flyway, web server, scheduler or Kafka listener.

```
java -Dloader.main=until.the.eternity.das.user.outbox.UserInfoArcanaRefreshCommand -cp auth.jar org.springframework.boot.loader.launch.PropertiesLauncher --app.arcana-refresh.enabled=true --app.arcana-refresh.action=dry-run
```

Use `--app.arcana-refresh.action=run --app.arcana-refresh.confirm=REFRESH_ARCANA_V1`
and optional `--app.arcana-refresh.batch-size=100` (1..1000). Repeat bounded runs
until remaining=0. Each transaction locks users in ID order, queues the current
full snapshot, and sets the replay marker atomically. Repeating a completed batch
does not increment revisions. Interrupted runs resume safely. Ordinary snapshot
writers preserve the marker. Keep existing publisher retries and verify outbox
drain/projection before exposing the new author label. Production execution is a
separate approved operation; implementation does not run it automatically.

Community author responses: feed/detail `author.arcana`, comments/replies and
legacy post/comment activity summaries `arcana`, nullable strings. Existing
batched user-summary reads supply the field without browser per-author requests.
Existing verification-based activity-grade visibility remains unchanged.

Validation covers profile set/change/clear, post-drain replay after an old
publisher acknowledgement, bounded resume/idempotency on MySQL, Kafka missing
vs explicit null/stale revision/invalid code, and author/comment mapping.
