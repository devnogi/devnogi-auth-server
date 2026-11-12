package until.the.eternity.das.hero;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class HeroModels {
    private HeroModels() {}

    public record Slide(
            long id,
            String imageUrl,
            String altText,
            String linkUrl,
            int position,
            boolean advertisement,
            Instant startsAt,
            Instant endsAt,
            Instant deletedAt) {}

    public record Display(List<Slide> items, Instant nextChangeAt, Instant serverTime) {}

    public record Creative(
            long id,
            String imageUrl,
            String altText,
            String linkUrl,
            boolean blocked,
            Instant createdAt) {}

    public record Auction(
            long id,
            LocalDate weekStart,
            int slot,
            String status,
            Instant opensAt,
            Instant closesAt,
            Instant displayStartsAt,
            Instant displayEndsAt,
            long highestBid,
            Long highestBidderId,
            Long winningBidId,
            String reason) {}

    public record Auctions(
            List<Auction> auctions,
            Instant serverTime,
            boolean enabled,
            Instant scheduledStartAt) {}

    public record Bid(
            long id,
            long auctionId,
            long creativeId,
            long amount,
            String status,
            String reason,
            Instant createdAt) {}

    public record Charge(long id, long auctionId, String kind, long amount, Instant createdAt) {}

    public record History(List<Bid> bids, List<Charge> charges) {}

    public record BidRequest(
            long creativeId,
            @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                            using = HeroIntegerDeserializer.class)
                    long amount,
            String idempotencyKey) {}

    public record OrderRequest(List<Long> ids) {}

    public record ReasonRequest(String reason) {}
}
