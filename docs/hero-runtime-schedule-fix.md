# Hero runtime proxy and schedule fix — revision 1

Date: 2026-11-12. Baseline Auth main `fbdca5004b1666f446ebce5793f61cf827ac2463`.

## Fixed acceptance contract

- Public Hero, admin slides/creatives, personal history and scheduled coordination must work
  with the actual Spring repository exception-translation CGLIB proxy enabled.
- SQL-owning services inject JdbcTemplate directly. HeroRepository's dependency becomes private;
  callers must not access dependency fields on a potentially proxied bean.
- Both current and admin auction response add nullable `scheduledStartAt` (UTC ISO timestamp).
  It is the configured Korean Monday midnight only when configuration is enabled and the
  configured start is still in the future. Disabled or already started means null. Existing
  `enabled` continues to describe whether auctions are currently enabled, not future intent.
- `2026-11-16` Asia/Seoul must serialize as `2026-11-15T15:00:00Z`.
- No bidding, XP, settlement, schema, schedule or permissions policy changes.

## Reproduction and verification

The prior MySQL slice did not install the repository exception translation postprocessor.
The regression installs the real postprocessor, asserts `AopUtils.isAopProxy(repository)`,
and exercises reads plus scheduler reconciliation before and at the configured start.
Before the fix the targeted MySQL test failed at `HeroAuctionService.lockCoordination`:
`Cannot invoke JdbcTemplate.queryForObject ... because this.repo.jdbc is null`.

Run the complete Hero and Experience MySQL suites, schedule/controller tests, standard
`gradlew.bat verify --no-daemon` and workflow contract check. Existing production deployment
still requires separate approval; this patch prepares a versioned PR only.

Verified on 2026-11-12:

- Before-fix targeted proxy regression: failed with the production-equivalent null field access.
- After-fix MySQL 8.0.42: HeroMySqlTest 30, ExperienceMySqlTest 15, HeroScheduleTest 2;
  all 47 passed with zero skipped. Database was a fresh disposable local schema.
- Standard `spotlessApply verify --no-daemon --max-workers=2`: passed, including PMD,
  formatting, tests and `das-1.0.14-SNAPSHOT.jar` build.
- `node --test scripts/workflow-contract.test.mjs`: 3 passed.
- `git diff --check`: passed.
