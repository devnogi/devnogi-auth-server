package until.the.eternity.das.hero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import until.the.eternity.das.experience.ActivityEvent;
import until.the.eternity.das.experience.ActivityGrade;
import until.the.eternity.das.experience.ExperienceOperations;
import until.the.eternity.das.experience.ExperienceQueryService;
import until.the.eternity.das.experience.ExperienceService;
import until.the.eternity.das.experience.ExperienceWalletService;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;
import until.the.eternity.das.user.entity.enums.Status;
import until.the.eternity.das.user.outbox.UserInfoOutboxService;

/** Runs only against an explicitly supplied disposable MySQL schema, never application config. */
@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "hero.auctions.enabled=true",
            "hero.auctions.starts-on=2026-11-16"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    HeroRepository.class,
    HeroAuctionService.class,
    HeroContentService.class,
    ExperienceWalletService.class,
    ExperienceService.class,
    ExperienceQueryService.class,
    ExperienceOperations.class,
    UserInfoOutboxService.class,
    HeroMySqlTest.Configuration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "EXPERIENCE_MYSQL_REQUIRED", matches = "true")
class HeroMySqlTest {
    private static final LocalDate WEEK = LocalDate.of(2026, 11, 16);
    private static final Instant OPEN = Instant.parse("2026-11-15T15:00:00Z");
    private static final Instant CLOSE = Instant.parse("2026-11-22T13:00:00Z");
    private static final Instant DISPLAY = Instant.parse("2026-11-22T15:00:00Z");
    private static final MockMultipartFile IMAGE =
            new MockMultipartFile("image", "test.png", "image/png", new byte[] {1});

    @TestConfiguration
    static class Configuration {
        @Bean
        MutableClock clock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(OPEN);

        void set(Instant value) {
            now.set(value);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("EXPERIENCE_MYSQL_URL"));
        p.add("spring.datasource.username", () -> System.getenv("EXPERIENCE_MYSQL_USER"));
        p.add("spring.datasource.password", () -> System.getenv("EXPERIENCE_MYSQL_PASSWORD"));
        p.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        p.add("spring.datasource.hikari.data-source-properties.connectionTimeZone", () -> "UTC");
        p.add(
                "spring.datasource.hikari.data-source-properties.forceConnectionTimeZoneToSession",
                () -> "true");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired HeroAuctionService auctions;
    @Autowired HeroContentService content;
    @Autowired ExperienceWalletService wallet;
    @Autowired ExperienceService activities;
    @Autowired ExperienceQueryService experience;
    @Autowired ExperienceOperations operations;
    @Autowired UserRepository users;
    @Autowired TransactionTemplate transactions;
    @Autowired MutableClock clock;
    @MockitoBean HeroMedia media;

    @BeforeEach
    void prepareDedicatedSchema() {
        clean();
        clock.set(OPEN.plusSeconds(3600));
        when(media.upload(any())).thenReturn("https://www.memonogi.com/test/hero.png");
        jdbc.update(
                "INSERT INTO hero_slide(image_url,alt_text,position,created_at,updated_at) VALUES ('/images/default.png','Default',1,UTC_TIMESTAMP(),UTC_TIMESTAMP())");
        jdbc.update("UPDATE experience_activity SET rollout_at=?", time(OPEN));
        auctions.createCurrentWeek();
    }

    @AfterEach
    void clean() {
        for (String table :
                List.of(
                        "hero_placement",
                        "hero_charge",
                        "hero_reservation",
                        "hero_bid",
                        "hero_auction",
                        "hero_week",
                        "hero_creative",
                        "hero_audit",
                        "hero_slide")) {
            jdbc.update("DELETE FROM " + table);
        }
        for (String table :
                List.of(
                        "user_experience_history",
                        "user_experience",
                        "user_info_outbox",
                        "user_verification_history",
                        "user_game_profile")) {
            jdbc.update(
                    "DELETE FROM "
                            + table
                            + " WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'hero-it-%')");
        }
        jdbc.update("DELETE FROM users WHERE email LIKE 'hero-it-%'");
    }

    @Test
    void firstBidRequiresTenXpAndOwnUnblockedCreative() {
        long user = user(45);
        long stranger = user(45);
        long creative = creative(user);
        assertThatThrownBy(() -> bid(user, 1, creative, 9)).isInstanceOf(HeroException.class);
        assertThatThrownBy(() -> bid(stranger, 1, creative, 10)).isInstanceOf(HeroException.class);
        bid(user, 1, creative, 10);
        assertThat(wallet.snapshot(user).reservedXp()).isEqualTo(10);
        assertThat(wallet.snapshot(user).currentXp()).isEqualTo(45);
    }

    @Test
    void seniorBoundaryAndVerificationAreAuthoritativeAtBidTime() {
        long junior = user(44);
        long unverified = user(100);
        jdbc.update("UPDATE users SET is_verified=FALSE WHERE id=?", unverified);
        assertThatThrownBy(() -> bid(junior, 1, creative(junior), 10))
                .isInstanceOf(HeroException.class);
        assertThatThrownBy(() -> bid(unverified, 1, creative(unverified), 10))
                .isInstanceOf(HeroException.class);
        assertThat(count("hero_reservation")).isZero();
    }

    @Test
    void higherBidReleasesPreviousReservationAndOwnRebidOnlyReservesDifference() {
        long first = user(60);
        long second = user(60);
        long firstCreative = creative(first);
        bid(first, 1, firstCreative, 40);
        bid(first, 1, firstCreative, 50);
        assertThat(wallet.snapshot(first).reservedXp()).isEqualTo(50);
        assertThatThrownBy(() -> bid(second, 1, creative(second), 50))
                .isInstanceOf(HeroException.class);
        bid(second, 1, creative(second), 51);
        assertThat(wallet.snapshot(first).reservedXp()).isZero();
        assertThat(wallet.snapshot(second).availableXp()).isEqualTo(9);
    }

    @Test
    void bothSlotsShareOneSpendableBalance() {
        long user = user(60);
        long creative = creative(user);
        bid(user, 1, creative, 40);
        assertThatThrownBy(() -> bid(user, 2, creative, 21)).isInstanceOf(HeroException.class);
        bid(user, 2, creative, 20);
        assertThat(wallet.snapshot(user).reservedXp()).isEqualTo(60);
    }

    @Test
    void exactRetryIsIdempotentButReusingKeyForDifferentPayloadConflicts() {
        long user = user(60);
        long creative = creative(user);
        var request = new HeroModels.BidRequest(creative, 10, "retry-key-123");
        var first = auctions.bid(user, auction(1), request);
        var retried = auctions.bid(user, auction(1), request);
        assertThat(retried.id()).isEqualTo(first.id());
        assertThat(count("hero_bid")).isEqualTo(1);
        assertThatThrownBy(
                        () ->
                                auctions.bid(
                                        user,
                                        auction(1),
                                        new HeroModels.BidRequest(
                                                creative, 11, request.idempotencyKey())))
                .isInstanceOf(HeroException.class);
        assertThat(wallet.snapshot(user).reservedXp()).isEqualTo(10);
    }

    @Test
    void concurrentEqualBidsHaveExactlyOneWinnerAndOneReservation() throws Exception {
        long first = user(60);
        long second = user(60);
        long firstCreative = creative(first);
        long secondCreative = creative(second);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(compete(start, first, firstCreative));
            var two = executor.submit(compete(start, second, secondCreative));
            start.countDown();
            assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(count("hero_bid")).isEqualTo(1);
        assertThat(count("hero_reservation")).isEqualTo(1);
    }

    @Test
    void closeBoundaryRejectsBidsEvenBeforeSchedulerRuns() {
        long user = user(60);
        long creative = creative(user);
        clock.set(CLOSE.minusNanos(1));
        bid(user, 1, creative, 10);
        clock.set(CLOSE);
        assertThatThrownBy(() -> bid(user, 1, creative, 11)).isInstanceOf(HeroException.class);
    }

    @Test
    void twoWinsUsePrechargeEligibilityAndPublishDemotionExactlyOnce() {
        long user = user(45);
        long creative = creative(user);
        bid(user, 1, creative, 10);
        bid(user, 2, creative, 10);
        clock.set(CLOSE);
        auctions.settle(WEEK);
        auctions.settle(WEEK);
        assertThat(wallet.snapshot(user).currentXp()).isEqualTo(25);
        assertThat(wallet.snapshot(user).reservedXp()).isZero();
        assertThat(count("hero_charge")).isEqualTo(2);
        assertThat(count("hero_placement")).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT activity_grade FROM user_info_outbox WHERE user_id=?",
                                String.class,
                                user))
                .isEqualTo("APPRENTICE");
        assertThat(experience.summary(user).activityGrade()).isEqualTo(ActivityGrade.APPRENTICE);
        assertThat(operations.reconcile().get("reservation_mismatches")).hasToString("0");
        assertThat(operations.reconcile().get("payment_mismatches")).hasToString("0");
    }

    @Test
    void underfundedCombinedWinsAreBothInvalidAndNeverCharge() {
        long user = user(100);
        long creative = creative(user);
        bid(user, 1, creative, 40);
        bid(user, 2, creative, 40);
        jdbc.update("UPDATE user_experience SET total_xp=60 WHERE user_id=?", user);
        clock.set(CLOSE);
        auctions.settle(WEEK);
        assertThat(count("hero_charge")).isZero();
        assertThat(count("hero_placement")).isZero();
        assertThat(wallet.snapshot(user).reservedXp()).isZero();
        assertThat(jdbc.queryForList("SELECT status FROM hero_bid", String.class))
                .containsOnly("INVALID");
    }

    @Test
    void invalidHighestBidDoesNotResurrectReleasedRunnerUp() {
        long first = user(60);
        long second = user(60);
        bid(first, 1, creative(first), 10);
        bid(second, 1, creative(second), 11);
        jdbc.update("UPDATE users SET is_verified=FALSE WHERE id=?", second);
        clock.set(CLOSE);
        auctions.settle(WEEK);
        assertThat(count("hero_charge")).isZero();
        assertThat(count("hero_placement")).isZero();
        assertThat(wallet.snapshot(first).reservedXp()).isZero();
    }

    @Test
    void demotionBeforeClosingInvalidatesOtherwiseAffordableBid() {
        long user = user(60);
        bid(user, 1, creative(user), 10);
        jdbc.update("UPDATE user_experience SET total_xp=40 WHERE user_id=?", user);
        clock.set(CLOSE);
        auctions.settle(WEEK);
        assertThat(count("hero_charge")).isZero();
        assertThat(wallet.snapshot(user).reservedXp()).isZero();
    }

    @Test
    void missingSettlementAtDisplayStartCancelsRatherThanChargingLate() {
        long user = user(60);
        bid(user, 1, creative(user), 10);
        clock.set(DISPLAY);
        auctions.settle(WEEK);
        assertThat(count("hero_charge")).isZero();
        assertThat(wallet.snapshot(user).reservedXp()).isZero();
        assertThat(
                        jdbc.queryForList(
                                "SELECT status FROM hero_auction WHERE week_start=?",
                                String.class,
                                WEEK))
                .containsOnly("CANCELLED");
    }

    @Test
    void cancellationRefundsBothSlotsOnlyOnceAndRestoresGrade() {
        long user = user(45);
        long creative = creative(user);
        long id = auction(1);
        bid(user, 1, creative, 10);
        bid(user, 2, creative, 10);
        clock.set(CLOSE);
        auctions.settle(WEEK);
        auctions.cancel(user, id, "Synthetic operations cancellation");
        auctions.cancel(user, id, "Synthetic retry");
        assertThat(wallet.snapshot(user).spentXp()).isZero();
        assertThat(wallet.snapshot(user).currentXp()).isEqualTo(45);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM hero_charge WHERE kind='REFUND'", Long.class))
                .isEqualTo(2);
        assertThat(experience.summary(user).activityGrade()).isEqualTo(ActivityGrade.SENIOR);
        assertThat(content.display(DISPLAY).items()).noneMatch(HeroModels.Slide::advertisement);
        assertThat(operations.reconcile().get("payment_mismatches")).hasToString("0");
    }

    @Test
    void blockingLeadingCreativeReleasesHoldAndPreventsRebid() {
        long user = user(60);
        long creative = creative(user);
        bid(user, 1, creative, 10);
        auctions.block(user, creative, "Synthetic policy violation");
        assertThat(wallet.snapshot(user).reservedXp()).isZero();
        assertThatThrownBy(() -> bid(user, 1, creative, 11)).isInstanceOf(HeroException.class);
    }

    @Test
    void blockingSettledCreativeStopsAdvertisementWithoutRefund() {
        long user = user(60);
        long creative = creative(user);
        bid(user, 1, creative, 10);
        clock.set(CLOSE);
        auctions.settle(WEEK);
        auctions.block(user, creative, "Synthetic policy violation");
        assertThat(wallet.snapshot(user).spentXp()).isEqualTo(10);
        assertThat(content.display(DISPLAY).items()).noneMatch(HeroModels.Slide::advertisement);
    }

    @Test
    void simultaneousModerationAndFirstBidNeverLeaveBlockedCreativeWithActiveReservation()
            throws Exception {
        long user = user(60);
        long creative = creative(user);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var bidding = executor.submit(() -> bidAfter(start, user, 1, creative, 10));
            var moderation =
                    executor.submit(
                            () -> {
                                start.await();
                                auctions.block(user, creative, "Synthetic moderation race");
                                return null;
                            });
            start.countDown();
            bidding.get(15, TimeUnit.SECONDS);
            moderation.get(15, TimeUnit.SECONDS);
        }
        assertThat(wallet.snapshot(user).reservedXp()).isZero();
        clock.set(CLOSE);
        auctions.settle(WEEK);
        assertThat(wallet.snapshot(user).spentXp()).isZero();
        assertThat(content.display(DISPLAY).items()).noneMatch(HeroModels.Slide::advertisement);
    }

    @Test
    void earlySettlementDoesNotFinalizeOpenBidsAndEmptySlotRemainsUncharged() {
        long user = user(60);
        bid(user, 1, creative(user), 10);
        auctions.settle(WEEK);
        assertThat(wallet.snapshot(user).reservedXp()).isEqualTo(10);
        assertThat(count("hero_charge")).isZero();
        clock.set(CLOSE);
        auctions.settle(WEEK);
        assertThat(count("hero_charge")).isEqualTo(1);
        assertThat(count("hero_placement")).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT winning_bid_id FROM hero_auction WHERE week_start=? AND slot=2",
                                Long.class,
                                WEEK))
                .isNull();
    }

    @Test
    void secondSlotWinnerKeepsSecondPositionAndEndBoundaryRemovesAd() {
        long user = user(60);
        bid(user, 2, creative(user), 10);
        clock.set(CLOSE);
        auctions.settle(WEEK);
        var display = content.display(DISPLAY);
        assertThat(display.items()).hasSize(2);
        assertThat(display.items().get(0).advertisement()).isFalse();
        assertThat(display.items().get(1).advertisement()).isTrue();
        assertThat(display.items().get(1).position()).isEqualTo(2);
        assertThat(content.display(DISPLAY.plusSeconds(7 * 86400)).items())
                .noneMatch(HeroModels.Slide::advertisement);
    }

    @Test
    void tenDefaultsAndTwoAdvertisementsStillRenderOnlyTenSlides() {
        long user = user(60);
        for (int i = 0; i < 9; i++)
            content.createSlide(user, IMAGE, "Slide " + i, null, null, null);
        assertThatThrownBy(() -> content.createSlide(user, IMAGE, "Eleventh", null, null, null))
                .isInstanceOf(HeroException.class);
        long creative = creative(user);
        bid(user, 1, creative, 10);
        bid(user, 2, creative, 10);
        clock.set(CLOSE);
        auctions.settle(WEEK);
        assertThat(content.display(DISPLAY).items())
                .hasSize(10)
                .filteredOn(HeroModels.Slide::advertisement)
                .hasSize(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM hero_slide WHERE deleted_at IS NULL",
                                Long.class))
                .isEqualTo(10);
    }

    @Test
    void lastDefaultCannotBeDeletedAndScheduledDefaultsUseHalfOpenPeriods() {
        long user = user(60);
        long first = jdbc.queryForObject("SELECT id FROM hero_slide", Long.class);
        assertThatThrownBy(() -> content.deleteSlide(user, first))
                .isInstanceOf(HeroException.class);
        var scheduled =
                content.createSlide(
                        user,
                        IMAGE,
                        "Scheduled",
                        null,
                        OPEN.plusSeconds(100),
                        OPEN.plusSeconds(200));
        content.deleteSlide(user, first);
        assertThat(content.display(OPEN.plusSeconds(100)).items().getFirst().id())
                .isEqualTo(scheduled.id());
        assertThat(content.display(OPEN.plusSeconds(200)).items().getFirst().id())
                .isNotEqualTo(scheduled.id());
        assertThat(content.display(OPEN.plusSeconds(200)).items()).hasSize(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT deleted_at IS NOT NULL FROM hero_slide WHERE id=?",
                                Boolean.class,
                                first))
                .isTrue();
    }

    @Test
    void externalLinksAreRejectedByServerEvenWhenFrontendIsBypassed() {
        long user = user(60);
        assertThatThrownBy(
                        () ->
                                content.createSlide(
                                        user,
                                        IMAGE,
                                        "Invalid",
                                        "https://www.memonogi.com.evil.com",
                                        null,
                                        null))
                .isInstanceOf(HeroException.class);
        assertThatThrownBy(() -> content.createCreative(user, IMAGE, "Invalid", "https://evil.com"))
                .isInstanceOf(HeroException.class);
    }

    @Test
    void reorderingRequiresAnExactPermutationAndPersistsPublicOrder() {
        long user = user(60);
        long first = content.slides().getFirst().id();
        long second = content.createSlide(user, IMAGE, "Second", null, null, null).id();
        assertThatThrownBy(() -> content.reorder(user, List.of(first, first)))
                .isInstanceOf(HeroException.class);
        assertThatThrownBy(() -> content.reorder(user, List.of(second)))
                .isInstanceOf(HeroException.class);
        content.reorder(user, List.of(second, first));
        assertThat(content.display(clock.instant()).items())
                .extracting(HeroModels.Slide::id)
                .containsExactly(second, first);
    }

    @Test
    void appendingAfterDeletingEarlierSlidesKeepsAllSurvivingSlidesBeforeTheNewSlide() {
        long user = user(60);
        for (int i = 2; i <= 5; i++) {
            content.createSlide(user, IMAGE, "Slide " + i, null, null, null);
        }
        List<Long> original = content.slides().stream().map(HeroModels.Slide::id).toList();
        content.deleteSlide(user, original.get(0));
        content.deleteSlide(user, original.get(1));

        long appended = content.createSlide(user, IMAGE, "Appended", null, null, null).id();

        assertThat(content.slides())
                .extracting(HeroModels.Slide::id)
                .containsExactly(original.get(2), original.get(3), original.get(4), appended);
        assertThat(content.display(clock.instant()).items())
                .extracting(HeroModels.Slide::id)
                .containsExactly(original.get(2), original.get(3), original.get(4), appended);
    }

    @Test
    void adminCanModerateOlderLeadingScheduledAndLiveCreativesBeyondRecentUploadLimit() {
        long user = user(60);
        long olderUnused = creative(user);
        long advertisement = creative(user);
        bid(user, 1, advertisement, 10);
        for (int i = 0; i < 105; i++) creative(user);

        assertThat(content.creatives(null))
                .hasSize(101)
                .extracting(HeroModels.Creative::id)
                .contains(advertisement)
                .doesNotContain(olderUnused);

        clock.set(CLOSE);
        auctions.settle(WEEK);
        assertThat(content.creatives(null))
                .extracting(HeroModels.Creative::id)
                .contains(advertisement);

        clock.set(DISPLAY);
        assertThat(content.creatives(null))
                .extracting(HeroModels.Creative::id)
                .contains(advertisement);

        clock.set(DISPLAY.plusSeconds(7 * 86400));
        assertThat(content.creatives(null))
                .hasSize(100)
                .extracting(HeroModels.Creative::id)
                .doesNotContain(advertisement, olderUnused);
    }

    @Test
    void onlyOwnerMayDeleteAnUnusedCreativeAndUsedCreativeRemainsImmutable() {
        long user = user(60);
        long stranger = user(60);
        long unused = creative(user);
        assertThatThrownBy(() -> content.deleteCreative(stranger, unused))
                .isInstanceOf(HeroException.class);
        content.deleteCreative(user, unused);
        assertThatThrownBy(() -> bid(user, 1, unused, 10)).isInstanceOf(HeroException.class);
        long used = creative(user);
        bid(user, 1, used, 10);
        assertThatThrownBy(() -> content.deleteCreative(user, used))
                .isInstanceOf(HeroException.class);
        assertThat(content.creatives(user))
                .extracting(HeroModels.Creative::id)
                .containsExactly(used);
    }

    @Test
    void concurrentBidsOnDifferentSlotsCannotReserveMoreThanOneUsersBalance() throws Exception {
        long user = user(60);
        long creative = creative(user);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> bidAfter(start, user, 1, creative, 40));
            var two = executor.submit(() -> bidAfter(start, user, 2, creative, 40));
            start.countDown();
            assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(wallet.snapshot(user).reservedXp()).isEqualTo(40);
    }

    @Test
    void settlementFailureRollsBackChargePlacementReservationAndGrade() {
        long user = user(45);
        bid(user, 1, creative(user), 10);
        clock.set(CLOSE);
        assertThatThrownBy(
                        () ->
                                transactions.executeWithoutResult(
                                        status -> {
                                            auctions.settle(WEEK);
                                            throw new IllegalStateException("synthetic rollback");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count("hero_charge")).isZero();
        assertThat(count("hero_placement")).isZero();
        assertThat(wallet.snapshot(user).reservedXp()).isEqualTo(10);
        assertThat(experience.summary(user).activityGrade()).isEqualTo(ActivityGrade.SENIOR);
    }

    @Test
    void realActivityReversalAfterSettlementCreatesDebtWithoutRewritingActivityHistory() {
        long user = user(0);
        for (int i = 1; i <= 3; i++) activities.apply(post(user, i, 1, true));
        bid(user, 1, creative(user), 50);
        clock.set(CLOSE);
        auctions.settle(WEEK);
        activities.apply(post(user, 1, 2, false));
        var balance = wallet.snapshot(user);
        assertThat(balance.activityXp()).isEqualTo(40);
        assertThat(balance.spentXp()).isEqualTo(50);
        assertThat(balance.deficitXp()).isEqualTo(10);
        assertThat(balance.availableXp()).isZero();
        activities.apply(post(user, 4, 1, true));
        assertThat(wallet.snapshot(user).currentXp()).isEqualTo(10);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT SUM(delta_xp) FROM user_experience_history WHERE user_id=?",
                                Long.class,
                                user))
                .isEqualTo(60);
    }

    @Test
    void bidRacingWithActivityReversalNeverChargesAnUnaffordableWinner() throws Exception {
        long user = user(0);
        for (int i = 1; i <= 3; i++) activities.apply(post(user, i, 1, true));
        long creative = creative(user);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var bid =
                    executor.submit(
                            () -> {
                                start.await();
                                try {
                                    bid(user, 1, creative, 50);
                                } catch (HeroException expectedConflict) {
                                }
                                return null;
                            });
            var reversal =
                    executor.submit(
                            () -> {
                                start.await();
                                activities.apply(post(user, 1, 2, false));
                                return null;
                            });
            start.countDown();
            bid.get(15, TimeUnit.SECONDS);
            reversal.get(15, TimeUnit.SECONDS);
        }
        clock.set(CLOSE);
        auctions.settle(WEEK);
        assertThat(wallet.snapshot(user).activityXp()).isEqualTo(40);
        assertThat(wallet.snapshot(user).spentXp()).isZero();
        assertThat(wallet.snapshot(user).reservedXp()).isZero();
    }

    private Callable<Boolean> compete(CountDownLatch start, long user, long creative) {
        return () -> bidAfter(start, user, 1, creative, 10);
    }

    private boolean bidAfter(CountDownLatch start, long user, int slot, long creative, long amount)
            throws InterruptedException {
        start.await();
        try {
            bid(user, slot, creative, amount);
            return true;
        } catch (HeroException expectedConflict) {
            return false;
        }
    }

    private long user(long xp) {
        long id =
                transactions.execute(
                        status -> {
                            User user =
                                    User.builder()
                                            .email(
                                                    "hero-it-"
                                                            + UUID.randomUUID()
                                                            + "@example.invalid")
                                            .nickname(
                                                    "hero-"
                                                            + UUID.randomUUID()
                                                                    .toString()
                                                                    .substring(0, 12))
                                            .status(Status.ACTIVE)
                                            .build();
                            user.updateVerificationStatus(true, time(OPEN.minusSeconds(3600)));
                            return users.saveAndFlush(user).getId();
                        });
        jdbc.update(
                "INSERT INTO user_experience(user_id,total_xp,backfill_eligible,updated_at) VALUES (?,?,FALSE,?)",
                id,
                xp,
                time(OPEN));
        jdbc.update("UPDATE users SET grade=? WHERE id=?", ActivityGrade.fromXp(xp).name(), id);
        jdbc.update(
                "INSERT INTO user_verification_history(user_id,verified_at,verification_success,created_at,experience_valid_from) VALUES (?,?,TRUE,?,?)",
                id,
                time(OPEN.minusSeconds(3600)),
                time(OPEN),
                time(OPEN.minusSeconds(3600)));
        return id;
    }

    private long creative(long user) {
        return content.createCreative(user, IMAGE, "Advertisement", null).id();
    }

    private long auction(int slot) {
        return jdbc.queryForObject(
                "SELECT id FROM hero_auction WHERE week_start=? AND slot=?",
                Long.class,
                WEEK,
                slot);
    }

    private HeroModels.Bid bid(long user, int slot, long creative, long amount) {
        return auctions.bid(
                user,
                auction(slot),
                new HeroModels.BidRequest(creative, amount, UUID.randomUUID().toString()));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private static LocalDateTime time(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private ActivityEvent post(long user, long target, long revision, boolean active) {
        return new ActivityEvent(
                UUID.randomUUID().toString(),
                1,
                "POST:" + target + ":CREATE",
                "POST_PUBLISHED",
                user,
                user,
                "POST",
                target,
                "POST",
                target,
                active,
                revision,
                OPEN.plusSeconds(revision));
    }
}
