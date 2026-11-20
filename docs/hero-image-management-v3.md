# Hero image management — revision 3 (2026-11-20)

Provider: Auth. Consumer: React BFF and `/admin/hero`. Fixed shared plan SHA-256:
`A8BDC4440D8C8D6AB4C804EA56390A806DC545266DC683C621809CF786EAFD9E`.

## Changes

- Add `hero_slide.visible`, with all existing rows visible. Settings-row locking protects last-visible and last-default hide/delete constraints. Existing registration, metadata editing, order and schedules remain in force. Hidden defaults are absent from public and scheduled preview results.
- Make the approved `/home-hero-auction.jpg` banner the first default by migrating only an unchanged library seed (original URL/description, no link, no schedule, not deleted). Preserve other defaults' relative order and all customized/deleted rows. A pre-existing auction banner prevents duplication. Paid auction slots 1 and 2 retain precedence.
- Add paginated administrator creative search with owner, latest block reason and current bid/placement usage. Existing user/public/legacy administrator response shapes are preserved apart from the additive Slide visibility field.
- Add administrator unblock with required reason and audit. Unblock permits new bids but never revives finalized bid/placement records, reservations or charges. Existing block/no-refund and week-cancel/refund policies are unchanged.
- Keep ADMIN/SUPER_ADMIN security and existing mutation origin/header checks. No user artwork or bid history can be rewritten through the new APIs.
- Schedule/visibility gaps use the neutral `/home-hero-unavailable.svg` system placeholder with no link, never the managed promotional image. This prevents a hidden/deleted/expired promotion from reappearing and preserves slot 2 when slot 1 has no active default.

The precise endpoint and response contract is in [hero-api.md](hero-api.md).

## Rollout

Deploy React with the approved asset first, then Auth. If an operator previously customized or deleted the original seed, the migration deliberately does not replace it; use the existing admin upload/order workflow to choose the banner explicitly. No production data is changed by local verification. Merge/deployment still requires separate human approval.

## Verification

`HeroControllerTest` covers strict boolean input, both administrator roles, member/anonymous rejection, existing CSRF guards and additive response metadata. `HeroAdminCreativeSearchTest` rejects invalid page/size/query/status before DB access. `HeroMySqlTest` covers visibility concurrency and guards, migration preservation, full-history search and literal filters, current usage counts, unblock audit/idempotency and unchanged stopped bids/placements/XP. Existing auction settlement/slot-priority tests remain in the same suite.

Run `gradlew.bat verify --no-daemon --max-workers=2` under Java 21. Set `EXPERIENCE_MYSQL_REQUIRED=true` and synthetic `EXPERIENCE_MYSQL_URL/USER/PASSWORD` for the disposable MySQL tests; without these, do not claim DB/locking coverage.

### Local verification result (2026-11-20)

- Java 21, MySQL 8.0.42 disposable schema: targeted `test --tests '*Hero*'` passed all 86 tests, including all 40 `HeroMySqlTest` cases, with zero skips/failures.
- Standard `verify --no-daemon --max-workers=2` passed Spotless, PMD, tests and `das-1.0.15-SNAPSHOT.jar`. There were 296 discovered tests: 259 passed, 37 unrelated opt-in integration tests skipped because their separate environment switches were not supplied. Hero/experience MySQL verification was enabled.
- `node --test scripts/workflow-contract.test.mjs`: 3 passed. `git diff --check`: passed.
- Local evidence: `build/hero-targeted-verification.log`, `build/hero-full-verification.log`, `build/hero-workflow-contract.log`, `build/hero-verification-summary.json`. An initial formatting-only full-verify failure was corrected with explicit Spotless formatting before the successful run; its log is retained separately.
