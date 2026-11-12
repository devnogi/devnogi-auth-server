# Hero auctions operations

The feature is disabled by default. After deploying Auth then React and validating the XP
backfill/reconciliation and member grade pipeline, set `HERO_AUCTIONS_ENABLED=true` and
`HERO_AUCTIONS_STARTS_ON` to the next Monday (`YYYY-MM-DD`, Asia/Seoul). Enabling without
a Monday start date fails startup. These are Spring relaxed-binding environment properties
for `hero.auctions.enabled` and `hero.auctions.starts-on`.

Each minute and on startup the scheduler creates this week's two auctions and reconciles
OPEN weeks. Transactions use a dedicated coordination row (`hero_settings.id=2`), then
week rows, users ascending, then creative rows. Admin image operations use a separate row
(`id=1`) so an image upload cannot hold the auction coordination lock.

The `hero_charge` immutable signed ledger stores charges positive and refunds negative.
`hero_reservation` contains only current holds. Existing activity reconciliation additionally
checks reservation/leader and payment/winner invariants. Investigate nonzero mismatches;
never repair by deleting ledger entries. Scheduler errors are logged and retried each minute.

Shutdown: stop accepting new bids by disabling the feature, and use the authenticated admin
cancel endpoint for every settled future/active week. Cancelling either slot cancels the full
week, releases holds, stops placements, and refunds each charge once. Disabled reconciliation
automatically cancels OPEN weeks only; it deliberately does not silently refund previously
settled contracts. Verify no open reservations and that expected refunds are present before
considering shutdown complete. Do not merely remove UI access.

Soft deletion preserves images referenced by bids, placements, and audit history. User creatives
cannot be changed after upload; a new creative and higher bid are needed to change material.
Storage rollback cleanup removes new objects when a transaction fails. Unreferenced historical
objects are retained for audit; no destructive automated storage cleanup is introduced.

API mutations require `X-Hero-Action: 1` even on direct Auth routes. Cross-site browser mutation
requests are rejected. BFF additionally checks Origin; Auth retains cookie JWT/role checks.
Maximum Hero multipart size is 10 MiB with 12 MiB request overhead; decoded images are capped
at 24 million pixels. Link validation never makes outbound network requests.
