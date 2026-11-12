package until.the.eternity.das.hero;

import static until.the.eternity.das.hero.HeroRepository.time;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import until.the.eternity.das.experience.ExperienceWalletService;
import until.the.eternity.das.hero.HeroModels.*;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.enums.Status;

@Service
public class HeroAuctionService {
    static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private final HeroRepository repo;
    private final EntityManager em;
    private final ExperienceWalletService wallet;
    private final HeroContentService content;
    private final Clock clock;
    private final boolean enabled;
    private final LocalDate startsOn;

    public HeroAuctionService(
            HeroRepository repo,
            EntityManager em,
            ExperienceWalletService wallet,
            HeroContentService content,
            Clock clock,
            @Value("${hero.auctions.enabled:false}") boolean enabled,
            @Value("${hero.auctions.starts-on:}") String startsOn) {
        this.repo = repo;
        this.em = em;
        this.wallet = wallet;
        this.content = content;
        this.clock = clock;
        this.enabled = enabled;
        this.startsOn = startsOn.isBlank() ? null : LocalDate.parse(startsOn);
        if (enabled && (this.startsOn == null || this.startsOn.getDayOfWeek() != DayOfWeek.MONDAY))
            throw new IllegalArgumentException(
                    "Enabled Hero auctions require a Monday starts-on date");
    }

    public LocalDate currentWeek() {
        return clock.instant()
                .atZone(KOREA)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public boolean enabled() {
        return enabled && startsOn != null && !currentWeek().isBefore(startsOn);
    }

    @Transactional
    public void createCurrentWeek() {
        lockCoordination();
        if (!enabled()) return;
        LocalDate week = currentWeek();
        Instant open = week.atStartOfDay(KOREA).toInstant();
        repo.jdbc.update(
                "INSERT IGNORE INTO hero_week(week_start,opens_at,closes_at,display_starts_at,display_ends_at) VALUES(?,?,?,?,?)",
                week,
                time(open),
                time(week.plusDays(6).atTime(22, 0).atZone(KOREA).toInstant()),
                time(week.plusWeeks(1).atStartOfDay(KOREA).toInstant()),
                time(week.plusWeeks(2).atStartOfDay(KOREA).toInstant()));
        lockWeek(week);
        for (int slot = 1; slot <= 2; slot++)
            repo.jdbc.update(
                    "INSERT IGNORE INTO hero_auction(week_start,slot) VALUES(?,?)", week, slot);
    }

    public List<LocalDate> pendingWeeks() {
        return repo.jdbc.query(
                "SELECT week_start FROM hero_week WHERE status='OPEN' ORDER BY week_start",
                (rs, row) -> rs.getDate(1).toLocalDate());
    }

    public Auctions current() {
        return new Auctions(
                repo.auctions(
                        "WHERE a.week_start>=? AND a.week_start<=? ORDER BY a.week_start DESC,a.slot",
                        currentWeek().minusWeeks(1),
                        currentWeek()),
                clock.instant(),
                enabled());
    }

    public Auctions all() {
        return new Auctions(
                repo.auctions("ORDER BY a.week_start DESC,a.slot LIMIT 52"),
                clock.instant(),
                enabled());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Bid bid(long userId, long auctionId, BidRequest request) {
        if (request == null
                || request.amount() < 10
                || request.amount() > 1_000_000_000L
                || request.idempotencyKey() == null
                || !request.idempotencyKey().matches("[A-Za-z0-9_-]{8,100}"))
            throw HeroException.invalid("입찰액 또는 요청 키가 올바르지 않습니다.");
        lockCoordination();
        Auction initial = repo.auction(auctionId);
        lockWeek(initial.weekStart());
        Auction auction = repo.auction(auctionId);
        List<Long> ids = new ArrayList<>();
        ids.add(userId);
        if (auction.highestBidderId() != null) ids.add(auction.highestBidderId());
        Map<Long, User> users = lockUsers(ids);
        List<Bid> previous =
                repo.jdbc.query(
                        "SELECT * FROM hero_bid WHERE user_id=? AND idempotency_key=?",
                        HeroRepository::bid,
                        userId,
                        request.idempotencyKey());
        if (!previous.isEmpty()) {
            Bid old = previous.getFirst();
            if (old.auctionId() != auctionId
                    || old.amount() != request.amount()
                    || old.creativeId() != request.creativeId())
                throw HeroException.conflict("같은 요청 키로 다른 입찰을 보낼 수 없습니다.");
            return old;
        }
        Instant now = clock.instant();
        if (!enabled()
                || !"OPEN".equals(auction.status())
                || now.isBefore(auction.opensAt())
                || !now.isBefore(auction.closesAt()))
            throw HeroException.conflict("입찰 가능한 시간이 아닙니다.");
        User user = users.get(userId);
        if (!eligible(user) || wallet.snapshot(userId).currentXp() < 45)
            throw HeroException.forbidden("인증된 수석 이상 회원만 참여할 수 있습니다.");
        if (request.amount() <= auction.highestBid())
            throw HeroException.conflict("현재 최고가보다 높은 금액을 입력해 주세요.");
        List<Long> owner =
                repo.jdbc.query(
                        "SELECT user_id FROM hero_creative WHERE id=? AND blocked=FALSE AND deleted_at IS NULL FOR UPDATE",
                        (rs, row) -> rs.getLong(1),
                        request.creativeId());
        if (owner.isEmpty() || owner.getFirst() != userId)
            throw HeroException.forbidden("사용 가능한 본인 광고 소재가 필요합니다.");
        long ownReserved =
                Objects.equals(auction.highestBidderId(), userId) ? auction.highestBid() : 0;
        if (request.amount() > Math.addExact(wallet.snapshot(userId).availableXp(), ownReserved))
            throw HeroException.conflict("사용 가능한 XP가 부족합니다.");
        repo.jdbc.update(
                "UPDATE hero_bid SET status='OUTBID',reason='더 높은 입찰' WHERE auction_id=? AND status='LEADING'",
                auctionId);
        repo.jdbc.update("DELETE FROM hero_reservation WHERE auction_id=?", auctionId);
        repo.jdbc.update(
                "INSERT INTO hero_bid(auction_id,user_id,creative_id,amount,status,idempotency_key,created_at) VALUES(?,?,?,?,'LEADING',?,?)",
                auctionId,
                userId,
                request.creativeId(),
                request.amount(),
                request.idempotencyKey(),
                time(now));
        long bidId = repo.insertedId();
        repo.jdbc.update(
                "UPDATE hero_auction SET highest_bid=?,highest_bidder_id=?,highest_bid_id=? WHERE id=?",
                request.amount(),
                userId,
                bidId,
                auctionId);
        repo.jdbc.update(
                "INSERT INTO hero_reservation(auction_id,user_id,amount) VALUES(?,?,?)",
                auctionId,
                userId,
                request.amount());
        return repo.bid(bidId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void settle(LocalDate week) {
        lockCoordination();
        lockWeek(week);
        List<Auction> auctions = repo.auctions("WHERE a.week_start=? ORDER BY a.slot", week);
        if (auctions.isEmpty() || auctions.stream().noneMatch(a -> "OPEN".equals(a.status())))
            return;
        Auction first = auctions.getFirst();
        if (clock.instant().isBefore(first.closesAt()) && enabled()) return;
        if (!enabled() || !clock.instant().isBefore(first.displayStartsAt())) {
            cancelLocked(0, auctions, !enabled() ? "운영 중단" : "정산 기한 초과");
            return;
        }
        Map<Long, User> users =
                lockUsers(
                        auctions.stream()
                                .map(Auction::highestBidderId)
                                .filter(Objects::nonNull)
                                .toList());
        Map<Long, Long> totals = new HashMap<>();
        for (Auction a : auctions)
            if (a.highestBidderId() != null)
                totals.merge(a.highestBidderId(), a.highestBid(), Math::addExact);
        Map<Long, Boolean> valid = new HashMap<>();
        totals.forEach(
                (id, amount) ->
                        valid.put(
                                id,
                                eligible(users.get(id))
                                        && wallet.snapshot(id).currentXp() >= 45
                                        && wallet.snapshot(id).currentXp() >= amount));
        for (Auction a : auctions) {
            Long bidId =
                    repo.jdbc.queryForObject(
                            "SELECT highest_bid_id FROM hero_auction WHERE id=?",
                            Long.class,
                            a.id());
            boolean material =
                    bidId != null
                            && Boolean.TRUE.equals(
                                    repo.jdbc.queryForObject(
                                            "SELECT NOT c.blocked AND c.deleted_at IS NULL FROM hero_bid b JOIN hero_creative c ON c.id=b.creative_id WHERE b.id=?",
                                            Boolean.class,
                                            bidId));
            boolean winner =
                    bidId != null && valid.getOrDefault(a.highestBidderId(), false) && material;
            repo.jdbc.update("DELETE FROM hero_reservation WHERE auction_id=?", a.id());
            if (winner) {
                repo.jdbc.update(
                        "INSERT INTO hero_charge(user_id,auction_id,kind,amount,created_at) VALUES(?,?,'CHARGE',?,?)",
                        a.highestBidderId(),
                        a.id(),
                        a.highestBid(),
                        time(clock.instant()));
                repo.jdbc.update(
                        "INSERT INTO hero_placement(auction_id,bid_id,slot,starts_at,ends_at) VALUES(?,?,?,?,?)",
                        a.id(),
                        bidId,
                        a.slot(),
                        time(a.displayStartsAt()),
                        time(a.displayEndsAt()));
                repo.jdbc.update("UPDATE hero_bid SET status='WON' WHERE id=?", bidId);
                repo.jdbc.update(
                        "UPDATE hero_auction SET status='SETTLED',winning_bid_id=? WHERE id=?",
                        bidId,
                        a.id());
            } else {
                String reason = bidId == null ? "입찰 없음" : "마감 시 자격·잔액 또는 소재 조건 미충족";
                if (bidId != null)
                    repo.jdbc.update(
                            "UPDATE hero_bid SET status='INVALID',reason=? WHERE id=?",
                            reason,
                            bidId);
                repo.jdbc.update(
                        "UPDATE hero_auction SET status='SETTLED',reason=? WHERE id=?",
                        reason,
                        a.id());
            }
        }
        users.values().forEach(wallet::recomputeGrade);
        repo.jdbc.update("UPDATE hero_week SET status='SETTLED' WHERE week_start=?", week);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancel(long actor, long auctionId, String reason) {
        lockCoordination();
        reason = HeroValidation.reason(reason);
        Auction a = repo.auction(auctionId);
        lockWeek(a.weekStart());
        cancelLocked(
                actor,
                repo.auctions("WHERE a.week_start=? ORDER BY a.slot", a.weekStart()),
                reason);
    }

    private void cancelLocked(long actor, List<Auction> auctions, String reason) {
        Map<Long, User> users =
                lockUsers(
                        auctions.stream()
                                .map(Auction::highestBidderId)
                                .filter(Objects::nonNull)
                                .toList());
        for (Auction a : auctions) {
            if ("CANCELLED".equals(a.status())) continue;
            repo.jdbc.update("DELETE FROM hero_reservation WHERE auction_id=?", a.id());
            repo.jdbc.update(
                    "INSERT IGNORE INTO hero_charge(user_id,auction_id,kind,amount,created_at) SELECT user_id,auction_id,'REFUND',-amount,? FROM hero_charge WHERE auction_id=? AND kind='CHARGE'",
                    time(clock.instant()),
                    a.id());
            repo.jdbc.update(
                    "UPDATE hero_placement SET stopped_at=? WHERE auction_id=? AND stopped_at IS NULL",
                    time(clock.instant()),
                    a.id());
            repo.jdbc.update(
                    "UPDATE hero_bid SET status='CANCELLED',reason=? WHERE auction_id=? AND status IN ('LEADING','WON','BLOCKED')",
                    reason,
                    a.id());
            repo.jdbc.update(
                    "UPDATE hero_auction SET status='CANCELLED',reason=? WHERE id=?",
                    reason,
                    a.id());
        }
        users.values().forEach(wallet::recomputeGrade);
        repo.jdbc.update(
                "UPDATE hero_week SET status='CANCELLED' WHERE week_start=?",
                auctions.getFirst().weekStart());
        content.audit(actor, "WEEK_CANCEL", auctions.getFirst().id(), reason);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void block(long actor, long creativeId, String reason) {
        lockCoordination();
        reason = HeroValidation.reason(reason);
        // Lock all referenced weeks before users and creative, same order as bids/settlement.
        List<LocalDate> weeks =
                repo.jdbc.query(
                        "SELECT DISTINCT a.week_start FROM hero_auction a JOIN hero_bid b ON b.auction_id=a.id WHERE b.creative_id=? ORDER BY a.week_start",
                        (rs, row) -> rs.getDate(1).toLocalDate(),
                        creativeId);
        weeks.forEach(this::lockWeek);
        List<Long> owners =
                repo.jdbc.query(
                        "SELECT user_id FROM hero_creative WHERE id=?",
                        (rs, row) -> rs.getLong(1),
                        creativeId);
        if (owners.isEmpty()) throw HeroException.missing();
        lockUsers(owners);
        repo.jdbc.queryForObject(
                "SELECT id FROM hero_creative WHERE id=? FOR UPDATE", Long.class, creativeId);
        repo.jdbc.update("UPDATE hero_creative SET blocked=TRUE WHERE id=?", creativeId);
        List<Long> leading =
                repo.jdbc.query(
                        "SELECT auction_id FROM hero_bid WHERE creative_id=? AND status='LEADING'",
                        (rs, row) -> rs.getLong(1),
                        creativeId);
        for (Long id : leading) {
            repo.jdbc.update("DELETE FROM hero_reservation WHERE auction_id=?", id);
            repo.jdbc.update(
                    "UPDATE hero_auction SET highest_bidder_id=NULL,highest_bid_id=NULL WHERE id=?",
                    id);
        }
        repo.jdbc.update(
                "UPDATE hero_placement SET stopped_at=? WHERE bid_id IN (SELECT id FROM hero_bid WHERE creative_id=?) AND stopped_at IS NULL",
                time(clock.instant()),
                creativeId);
        repo.jdbc.update(
                "UPDATE hero_bid SET status='BLOCKED',reason=? WHERE creative_id=? AND status IN ('LEADING','WON')",
                reason,
                creativeId);
        content.audit(actor, "CREATIVE_BLOCK", creativeId, reason);
    }

    public History history(long user) {
        List<Bid> bids =
                repo.jdbc.query(
                        "SELECT * FROM hero_bid WHERE user_id=? ORDER BY id DESC LIMIT 100",
                        HeroRepository::bid,
                        user);
        List<Charge> charges =
                repo.jdbc.query(
                        "SELECT * FROM hero_charge WHERE user_id=? ORDER BY id DESC LIMIT 100",
                        (rs, row) ->
                                new Charge(
                                        rs.getLong("id"),
                                        rs.getLong("auction_id"),
                                        rs.getString("kind"),
                                        rs.getLong("amount"),
                                        HeroRepository.instant(rs, "created_at")),
                        user);
        return new History(bids, charges);
    }

    private void lockCoordination() {
        repo.jdbc.queryForObject(
                "SELECT id FROM hero_settings WHERE id=2 FOR UPDATE", Integer.class);
    }

    private void lockWeek(LocalDate week) {
        repo.jdbc.queryForObject(
                "SELECT week_start FROM hero_week WHERE week_start=? FOR UPDATE",
                (rs, row) -> rs.getDate(1).toLocalDate(),
                week);
    }

    private Map<Long, User> lockUsers(List<Long> ids) {
        Map<Long, User> result = new HashMap<>();
        for (Long id : new TreeSet<>(ids)) {
            User user = em.find(User.class, id, LockModeType.PESSIMISTIC_WRITE);
            if (user == null) throw HeroException.missing();
            em.refresh(user);
            result.put(id, user);
        }
        return result;
    }

    private static boolean eligible(User user) {
        return user != null && user.isVerified() && user.getStatus() == Status.ACTIVE;
    }
}
