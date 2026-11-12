# Hero API v1 (2026-11-12)

All routes below are Auth paths, reached through `/das`; React BFF strips `/das`.
Responses use existing `CommonResponse<T>` (`data` below). Timestamps are ISO UTC.
Mutations require existing Auth access-token cookie; admin requires ADMIN/SUPER_ADMIN.

## Shapes

- Slide: `{id:number,imageUrl:string,altText:string,linkUrl:string|null,position:number,advertisement:boolean,startsAt:string|null,endsAt:string|null}`.
- Creative: `{id:number,imageUrl:string,altText:string,linkUrl:string|null,blocked:boolean,createdAt:string}`.
- Auction: `{id:number,weekStart:string,slot:number,status:string,opensAt:string,closesAt:string,displayStartsAt:string,displayEndsAt:string,highestBid:number,highestBidderId:number|null,winningBidId:number|null,reason:string|null}`. Status OPEN/SETTLED/CANCELLED; highestBid 0 means no bids; current response includes OPEN even after closing until settlement, so compare closesAt/serverTime.
- Bid: `{id:number,auctionId:number,creativeId:number,amount:number,status:string,reason:string|null,createdAt:string}`. Status LEADING/OUTBID/WON/INVALID/CANCELLED/BLOCKED.
- Charge: `{id:number,auctionId:number,kind:string,amount:number,createdAt:string}`. Positive CHARGE; negative REFUND.
- AdminSlide: Slide plus `{deletedAt:string|null}`; position is saved default ordering.

## Public

- GET `/api/hero` -> `{items:Slide[],nextChangeAt:string|null,serverTime:string}`; no-store.
- GET `/api/hero/auctions/current` -> `{auctions:Auction[],serverTime:string,enabled:boolean}`. Returns current week two slots plus preceding week's pair (results/active placements). UI selects current week for bids. Initial enabled=false until explicit configuration and next Monday start.

## User

- GET `/api/user/hero/creatives` -> `Creative[]`.
- POST `/api/user/hero/creatives` multipart fields `image` file, `altText` string, optional `linkUrl` -> Creative.
- DELETE `/api/user/hero/creatives/{id}` -> null; only own unreferenced creative.
- POST `/api/user/hero/auctions/{id}/bids` JSON `{creativeId:number,amount:number,idempotencyKey:string}` -> Bid. Key 8-100 ASCII alphanumeric/hyphen/underscore. Retry exact request returns prior bid; changed request with same key conflicts.
- GET `/api/user/hero/auctions` -> `{bids:Bid[],charges:Charge[]}` newest 100 each.

## Admin

- GET `/api/admin/hero/slides` -> AdminSlide[] (nondeleted).
- POST `/api/admin/hero/slides` multipart `image`,`altText`,optional `linkUrl`,`startsAt`,`endsAt` -> AdminSlide (append).
- PUT `/api/admin/hero/slides/{id}` multipart optional `image`,required `altText`,optional `linkUrl`,`startsAt`,`endsAt` -> AdminSlide. Empty optional fields clear values.
- DELETE `/api/admin/hero/slides/{id}` -> null.
- PUT `/api/admin/hero/slides/order` JSON `{ids:number[]}` -> null; exact permutation of all nondeleted slides.
- GET `/api/admin/hero/preview?at=<ISO instant>` -> public Hero shape.
- GET `/api/admin/hero/auctions` -> `{auctions:Auction[],serverTime:string,enabled:boolean}` (latest 26 weeks).
- GET `/api/admin/hero/creatives` -> Creative[] (latest 100 including blocked, plus every leading, scheduled and currently displayed creative regardless of age).
- POST `/api/admin/hero/creatives/{id}/block` JSON `{reason:string}` -> null.
- POST `/api/admin/hero/auctions/{id}/cancel` JSON `{reason:string}` -> null. Cancels both slots of that week, releases holds and refunds committed charges exactly once.

Hero images use existing public storage URL; seeded images use `/home-hero-library-v2.png` and `/home-hero-winter.png` on React origin. No arbitrary remote fetch API.

Errors use CommonResponse `success:false`, `code:HERO_*`, Korean message. HTTP 400 invalid input; 403 eligibility/ownership; 404 missing; 409 state/price/balance/idempotency conflict.
