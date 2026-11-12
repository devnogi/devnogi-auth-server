# Hero and weekly XP advertising — approved specification r1

Approved by the user on 2026-11-12. Provider: Auth; consumer: React. Implementation
baseline: Auth 490e059 and React 6accb3e, freshly fetched main. Existing worktrees
and uncommitted changes remain untouched. No production merge/deployment authorized.

## Acceptance contract

- Admin manages 1–10 nondeleted defaults: required image/alt, optional link,
  order, optional inclusive start/exclusive end. Delete is soft; last default
  cannot be deleted. Seed existing two images and retain built-in fallback.
- Won advertising slots keep positions 1 and 2. Fill unused positions with
  currently scheduled defaults, cap total at 10. Slot 2 alone must stay second.
  No eligible images or failed retrieval displays one built-in fallback.
- Same link policy in Java and TypeScript: absolute HTTPS, a valid subdomain of
  memonogi.com (apex excluded), no credentials/nonstandard port/backslash/control
  characters. Optional link; paths/query/fragment supported. Never fetch input URL.
- JPEG/PNG only; at most 10 MiB and 24 million decoded pixels; backend decodes
  actual file. Recommended 1920×640. Preview desktop/mobile cropping and search.
- Preserve seven-second carousel, pause/keyboard/reduced-motion behavior; disable
  navigation/rotation for one slide. Link clicks cannot intercept search/controls.
- Auctions: Monday 00:00 through Sunday 22:00 exclusive Asia/Seoul. Two independent
  slots settle together. Placement: next Monday 00:00 for seven days.
- Verified ACTIVE SENIOR+ (45 current XP) required at bid and settlement. Minimum
  10 integer XP; increase >=1. Images submitted before bid, no prior moderation.
- Only current highest bid reserves XP; self-raise reserves delta; outbid releases.
  Both slots may be won; total reservations must fit current XP. No cancel, price
  reduction, automatic bids, extensions or replacement of bound creative versions.
- Both winners checked from same precharge snapshot; underfunded combined winners
  are both invalidated. Ineligible/blocked highest bid is invalid; never revive
  released runner-up bids. Reserve release and default fallback on no valid winner.
- Moderator blocks creative (pending invalidation or scheduled/live stop, no
  refund for policy violations), or cancels auction for operational reasons
  (release holds/full refund exactly once). Reasons and actor are audited.
- Preserve activity ledger A. Separate signed charge/refund ledger S and active
  reservations R. current=max(0,A-S), available=max(0,A-S-R), deficit=max(0,S-A).
  Grade/progression use current; reservations do not lower grade. Reversals may
  create deficit, paid back by future earnings, without corrupting activity history.
- Week lock before ascending user locks. Bid/hold replacement atomic. Settlement,
  debit, hold release, placement, grade and summary outbox atomic and idempotent.
- Recover scheduler work on startup/every minute. Server time enforces cutoff.
  Unsettled week at placement start is cancelled/released, never charged late.
- XP summary adds current/spent/reserved/available/deficit while preserving totalXp
  as activity total. Separate advertising transaction history. Existing Community
  grade event contract is unchanged.
- Admin /admin/hero, public participant /hero-auctions, existing mypage XP update.
  BFF preserves Auth cookies and Origin/CSRF protection. Public Hero refreshes at
  boundaries, focus and every 30 seconds in active tab; expired ads removed offline.
- New admin APIs require ADMIN/SUPER_ADMIN. User resource identity comes from
  authenticated principal. Preserve static images until backend and frontend rollout.

## Verification and rollout

Unit/contracts: URL parity, image limits, periods/order/count, single slide,
keyboard/search isolation, owner/admin denial, immutable creatives, bid boundaries.
Real MySQL: complete migrations, concurrent bids/reversals, reservation replacement,
dual settlement, idempotency, grade/outbox, refunds, deficit/reconciliation, recovery.
Run Auth gradlew verify and React npm run verify; independent review actual diff
and results; version bumps and required PR CI. Auth additive migration first, React
next; activate first complete Monday only after XP activation and reconciliation.
Observe overdue settlement, reservation/payment anomalies and summary event failure.
Shutdown must release/refund unresolved financial obligations explicitly.

See hero-api.md for concrete HTTP contracts. Plan changes require documented
revision and updated acceptance checks; not silent omissions.
