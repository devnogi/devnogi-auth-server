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
- Slide also includes `visible:boolean`; public results are always visible. AdminSlide includes `{deletedAt:string|null}`; position is saved default ordering, including hidden defaults.

## Public

- GET `/api/hero` -> `{items:Slide[],nextChangeAt:string|null,serverTime:string}`; no-store.
- GET `/api/hero/auctions/current` -> `{auctions:Auction[],serverTime:string,enabled:boolean,scheduledStartAt:string|null}`. Returns current week two slots plus preceding week's pair (results/active placements). UI selects current week for bids. Initial enabled=false until explicit configuration and next Monday start.

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
- PUT `/api/admin/hero/slides/{id}/visibility` JSON `{visible:boolean}` -> null. The value must be a JSON boolean (omitted/null/string/number reject). The last visible default cannot be hidden or deleted; the last nondeleted default cannot be deleted. Create defaults to visible; editing media/metadata preserves visibility.
- PUT `/api/admin/hero/slides/order` JSON `{ids:number[]}` -> null; exact permutation of all nondeleted slides.
- GET `/api/admin/hero/preview?at=<ISO instant>` -> public Hero shape.
- GET `/api/admin/hero/auctions` -> `{auctions:Auction[],serverTime:string,enabled:boolean,scheduledStartAt:string|null}` (latest 26 weeks).
- GET `/api/admin/hero/creatives` -> Creative[] (latest 100 including blocked, plus every leading, scheduled and currently displayed creative regardless of age).
- POST `/api/admin/hero/creatives/{id}/block` JSON `{reason:string}` -> null.
- POST `/api/admin/hero/creatives/{id}/unblock` JSON `{reason:string}` -> null. Clears the block for future bids only. Previously blocked bids, stopped placements, reservations and XP charges/refunds are not restored or changed. Repeated unblock is a no-op. Missing/deleted creatives reject; reason is required (1–500 characters). Both actions are audited.
- GET `/api/admin/hero/creatives/search?page=0&size=20&query=&status=ALL` -> `{items:AdminHeroCreative[],page:number,size:number,totalElements:number,totalPages:number}`. Page is >=0, size is 1–50, query is <=100 characters, status is ALL/AVAILABLE/BLOCKED. Searches every nondeleted creative by literal partial description/link/nickname or exact creative/user ID, ordered by descending creative ID. An empty result has totalPages=0. The legacy creatives array endpoint is unchanged.
- AdminHeroCreative is Creative plus `{userId:number,nickname:string|null,blockReason:string|null,leadingBidCount:number,scheduledPlacementCount:number,activePlacementCount:number}`. Block reason is the latest block audit only while blocked. Usage counts exclude blocked/stopped records and follow current server time. These fields never appear in the public/user creative DTOs.
- POST `/api/admin/hero/auctions/{id}/cancel` JSON `{reason:string}` -> null. Cancels both slots of that week, releases holds and refunds committed charges exactly once.

Hero images use existing public storage URL. The untouched library seed becomes `/home-hero-auction.jpg` linking to `https://www.memonogi.com/hero-auctions`; `/home-hero-winter.png` is retained. Schedule/visibility gaps use the neutral `/home-hero-unavailable.svg` system placeholder (no link), never a hidden/deleted/expired managed banner. This also fills slot 1 when only a paid slot 2 is active. Deploy both React assets before the Auth migration. Customized/deleted library seeds and an already installed auction banner are preserved. No arbitrary remote fetch API.

Errors use CommonResponse `success:false`, `code:HERO_*`, Korean message. HTTP 400 invalid input; 403 eligibility/ownership; 404 missing; 409 state/price/balance/idempotency conflict.

## Scheduled start contract (runtime fix revision 1)

scheduledStartAt is the configured Asia/Seoul Monday midnight as UTC ISO timestamp only when configuration is enabled and that instant is still in the future. It is null when disabled or already started. The existing enabled field remains false before opening. Both current and admin auction responses use the same rule. Example: 2026-11-16 00:00 KST is 2026-11-15T15:00:00Z.
