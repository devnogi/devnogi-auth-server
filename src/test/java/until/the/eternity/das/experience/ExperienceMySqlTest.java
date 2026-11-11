package until.the.eternity.das.experience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;
import until.the.eternity.das.user.entity.enums.Status;
import until.the.eternity.das.user.outbox.UserInfoOutboxService;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    ExperienceService.class,
    ExperienceQueryService.class,
    ExperienceOperations.class,
    UserInfoOutboxService.class,
    ExperienceMySqlTest.Configuration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "EXPERIENCE_MYSQL_REQUIRED", matches = "true")
class ExperienceMySqlTest {
    private static TimeZone originalZone;

    @TestConfiguration
    static class Configuration {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
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
    @Autowired ExperienceService service;
    @Autowired ExperienceQueryService queries;
    @Autowired ExperienceOperations operations;
    @Autowired UserRepository users;
    @Autowired TransactionTemplate transactions;
    @Autowired UserInfoOutboxService summaries;

    @Autowired
    until.the.eternity.das.verification.entity.UserVerificationHistoryRepository
            verificationHistory;

    private Instant cutoff;

    @Test
    void utcDatabaseAndSeoulJvmPreserveFreezeHistoryAndJpaIntervals() {
        long member = user(true);
        Instant before = Instant.now().minusSeconds(1);
        freeze();
        assertThat(cutoff).isBetween(before, Instant.now().plusSeconds(1));
        assertThat(operations.freeze().toInstant(ZoneOffset.UTC)).isEqualTo(cutoff);
        assertThat(service.rolloutAt().toInstant(ZoneOffset.UTC)).isEqualTo(cutoff);
        ActivityEvent event = post(member, 1, 1, true);
        service.apply(event);
        var history = queries.history(member, null, 20).items().getFirst();
        assertThat(history.occurredAt()).isEqualTo(event.occurredAt());
        assertThat(Duration.between(history.processedAt(), Instant.now()).abs())
                .isLessThan(Duration.ofSeconds(10));
        LocalDateTime start = LocalDateTime.of(2026, 11, 11, 9, 0, 0);
        long intervalId =
                transactions.execute(
                        status ->
                                verificationHistory
                                        .saveAndFlush(
                                                until.the.eternity.das.verification.entity
                                                        .UserVerificationHistory.builder()
                                                        .user(users.findById(member).orElseThrow())
                                                        .verificationSuccess(true)
                                                        .verifiedAt(LocalDateTime.now())
                                                        .experienceValidFrom(start)
                                                        .build())
                                        .getId());
        assertThat(
                        jdbc.<LocalDateTime>queryForObject(
                                "SELECT experience_valid_from FROM user_verification_history WHERE id = ?",
                                (rs, row) -> rs.getObject(1, LocalDateTime.class),
                                intervalId))
                .isEqualTo(start);
        transactions.executeWithoutResult(
                status ->
                        verificationHistory.closeExperienceIntervals(
                                member, start.plusSeconds(10)));
        assertThat(
                        jdbc.<LocalDateTime>queryForObject(
                                "SELECT valid_until FROM user_verification_history WHERE id = ?",
                                (rs, row) -> rs.getObject(1, LocalDateTime.class),
                                intervalId))
                .isEqualTo(start.plusSeconds(10));
    }

    @Test
    void managedProfileSnapshotUsesLockedCurrentGradeInsteadOfRepeatableReadSnapshot()
            throws Exception {
        long member = user(true);
        freeze();
        CountDownLatch snapshotLoaded = new CountDownLatch(1);
        CountDownLatch promotionCommitted = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var writer =
                    executor.submit(
                            () -> {
                                transactions.executeWithoutResult(
                                        status -> {
                                            User stale = users.findById(member).orElseThrow();
                                            snapshotLoaded.countDown();
                                            try {
                                                if (!promotionCommitted.await(10, TimeUnit.SECONDS))
                                                    throw new IllegalStateException(
                                                            "promotion timeout");
                                            } catch (InterruptedException exception) {
                                                Thread.currentThread().interrupt();
                                                throw new IllegalStateException(exception);
                                            }
                                            stale.updateUserInfo("xp-profile-" + member, null);
                                            summaries.enqueue(stale);
                                        });
                                return null;
                            });
            assertThat(snapshotLoaded.await(10, TimeUnit.SECONDS)).isTrue();
            for (int i = 1; i <= 3; i++) service.apply(post(member, i, i, true));
            promotionCommitted.countDown();
            writer.get(10, TimeUnit.SECONDS);
        } finally {
            promotionCommitted.countDown();
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT grade FROM users WHERE id = ?", String.class, member))
                .isEqualTo("SENIOR");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT activity_grade FROM user_info_outbox WHERE user_id = ?",
                                String.class,
                                member))
                .isEqualTo("SENIOR");
    }

    @Test
    void detachedOAuthStyleSaveCannotOverwriteConcurrentPromotionOrPublishStaleGrade()
            throws Exception {
        long member = user(true);
        freeze();
        CountDownLatch snapshotLoaded = new CountDownLatch(1);
        CountDownLatch promotionCommitted = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var writer =
                    executor.submit(
                            () -> {
                                User stale = users.findById(member).orElseThrow();
                                snapshotLoaded.countDown();
                                if (!promotionCommitted.await(10, TimeUnit.SECONDS))
                                    throw new IllegalStateException("promotion timeout");
                                stale.updateLastLoginAt();
                                transactions.executeWithoutResult(
                                        status -> {
                                            User merged = users.saveAndFlush(stale);
                                            summaries.enqueue(merged);
                                        });
                                return null;
                            });
            assertThat(snapshotLoaded.await(10, TimeUnit.SECONDS)).isTrue();
            for (int i = 1; i <= 3; i++) service.apply(post(member, i, i, true));
            promotionCommitted.countDown();
            writer.get(10, TimeUnit.SECONDS);
        } finally {
            promotionCommitted.countDown();
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT grade FROM users WHERE id = ?", String.class, member))
                .isEqualTo("SENIOR");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT activity_grade FROM user_info_outbox WHERE user_id = ?",
                                String.class,
                                member))
                .isEqualTo("SENIOR");
        assertThat(operations.reconcile().get("grade_mismatches")).hasToString("0");
    }

    @Test
    void managedStatusWriterCannotOverwriteConcurrentPromotion() throws Exception {
        long member = user(true);
        freeze();
        CountDownLatch snapshotLoaded = new CountDownLatch(1);
        CountDownLatch promotionCommitted = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var writer =
                    executor.submit(
                            () -> {
                                transactions.executeWithoutResult(
                                        status -> {
                                            User stale = users.findById(member).orElseThrow();
                                            snapshotLoaded.countDown();
                                            try {
                                                if (!promotionCommitted.await(10, TimeUnit.SECONDS))
                                                    throw new IllegalStateException(
                                                            "promotion timeout");
                                            } catch (InterruptedException exception) {
                                                Thread.currentThread().interrupt();
                                                throw new IllegalStateException(exception);
                                            }
                                            stale.updateUserStatus(Status.INACTIVE);
                                        });
                                return null;
                            });
            assertThat(snapshotLoaded.await(10, TimeUnit.SECONDS)).isTrue();
            for (int i = 1; i <= 3; i++) service.apply(post(member, i, i, true));
            promotionCommitted.countDown();
            writer.get(10, TimeUnit.SECONDS);
        } finally {
            promotionCommitted.countDown();
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT grade FROM users WHERE id = ?", String.class, member))
                .isEqualTo("SENIOR");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM users WHERE id = ?", String.class, member))
                .isEqualTo("INACTIVE");
        assertThat(operations.reconcile().get("grade_mismatches")).hasToString("0");
    }

    @Test
    void reconciliationFindsMissingGradeAndMissingAggregate() {
        long member = user(true);
        freeze();
        service.apply(post(member, 1, 1, true));
        jdbc.update("UPDATE users SET grade = NULL WHERE id = ?", member);
        assertThat(operations.reconcile().get("grade_mismatches")).hasToString("1");
        jdbc.update("DELETE FROM user_experience WHERE user_id = ?", member);
        assertThat(operations.reconcile().get("ledger_mismatches")).hasToString("1");
    }

    @Test
    void freezeReconcilesRollingDeploymentIntervalsWithoutFictionalVerificationHistory() {
        long current = user(true);
        long lost = user(false);
        interval(
                lost,
                Instant.now().minusSeconds(3600)); // old node changed flag without closing interval
        int visibleBefore =
                verificationHistory.findTop100ByUserIdOrderByVerifiedAtDesc(current).size();
        jdbc.update("UPDATE users SET grade = NULL WHERE id = ?", current);
        jdbc.update(
                "INSERT INTO user_info_outbox(user_id, revision, published_revision, nickname, verified, attempt_count, next_attempt_at, pending_since, created_at, updated_at) VALUES (?, 2, 2, 'old-publisher', TRUE, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                current);
        freeze();
        assertThat(
                        jdbc.queryForMap(
                                "SELECT activity_grade, revision, published_revision FROM user_info_outbox WHERE user_id = ?",
                                current))
                .containsEntry("activity_grade", "APPRENTICE")
                .containsEntry("revision", 3L)
                .containsEntry("published_revision", 2L);
        assertThat(verificationHistory.findTop100ByUserIdOrderByVerifiedAtDesc(current))
                .hasSize(visibleBefore);
        service.apply(post(current, 1, 1, true));
        service.apply(post(lost, 2, 2, true));
        assertThat(queries.summary(current).totalXp()).isEqualTo(20);
        assertThat(queries.summary(lost).totalXp()).isZero();
    }

    @BeforeAll
    static void seoulTimezone() {
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

    @AfterAll
    static void restoreTimezone() {
        TimeZone.setDefault(originalZone);
    }

    @BeforeEach
    void resetDedicatedSchema() {
        jdbc.update("DELETE FROM user_experience_history");
        jdbc.update("DELETE FROM user_experience");
        jdbc.update("DELETE FROM user_info_outbox");
        jdbc.update("DELETE FROM user_verification_history");
        jdbc.update("DELETE FROM users WHERE email LIKE 'xp-it-%'");
        jdbc.update("UPDATE experience_activity SET rollout_at = NULL");
    }

    @Test
    void duplicateReverseOrderReactivationAndDemotionPreserveLedger() {
        long user = user(true);
        freeze();
        for (int i = 1; i <= 3; i++) service.apply(post(user, i, i, true));
        assertThat(queries.summary(user).activityGrade()).isEqualTo(ActivityGrade.SENIOR);
        ActivityEvent deletion = post(user, 1, 10, false);
        service.apply(deletion);
        service.apply(deletion);
        service.apply(post(user, 1, 1, true));
        assertThat(queries.summary(user).totalXp()).isEqualTo(40);
        assertThat(queries.summary(user).activityGrade()).isEqualTo(ActivityGrade.APPRENTICE);
        service.apply(post(user, 9, 20, false));
        service.apply(post(user, 9, 0, true));
        assertThat(queries.summary(user).totalXp()).isEqualTo(40);
        assertThat(operations.reconcile().get("ledger_mismatches")).hasToString("0");
        assertThat(operations.reconcile().get("grade_mismatches")).hasToString("0");
    }

    @Test
    void likesBothSidesSelfLikeAndReverification() {
        long actor = user(false);
        long author = user(true);
        freeze();
        service.apply(like(actor, author, 1, true));
        assertThat(queries.summary(actor).totalXp()).isZero();
        assertThat(queries.summary(author).totalXp()).isEqualTo(2);
        service.apply(like(actor, author, 2, false));
        verify(actor, cutoff.plusSeconds(2));
        service.apply(like(actor, author, 3, true));
        service.apply(like(actor, author, 3, true));
        assertThat(queries.summary(actor).totalXp()).isEqualTo(1);
        assertThat(queries.summary(author).totalXp()).isEqualTo(2);
        service.apply(like(author, author, 4, true));
        assertThat(queries.summary(author).totalXp()).isEqualTo(2);
        service.apply(like(actor, author, 5, false));
        service.apply(like(actor, author, 6, true));
        assertThat(queries.summary(actor).totalXp()).isEqualTo(1);
        assertThat(queries.summary(author).totalXp()).isEqualTo(2);
    }

    @Test
    void occurredAtEligibilitySurvivesDelayLossAndReversal() {
        long user = user(true);
        freeze();
        loseVerification(user, cutoff.plusSeconds(5));
        service.apply(post(user, 1, 1, true)); // delivered after loss, happened before loss
        service.apply(post(user, 2, 7, true)); // happened during loss
        assertThat(queries.summary(user).totalXp()).isEqualTo(20);
        assertThat(queries.summary(user).activityGrade()).isNull();
        service.apply(post(user, 1, 10, false));
        assertThat(queries.summary(user).totalXp()).isZero();
    }

    @Test
    void backfillCohortIsFrozenAndReplayCannotResurrectDeletedContent() {
        long member = user(true);
        long newcomer = user(false);
        freeze();
        LocalDateTime first = operations.freeze();
        verify(newcomer, cutoff.plusSeconds(1));
        loseVerification(member, cutoff.plusSeconds(2));
        assertThat(operations.freeze()).isEqualTo(first);
        service.apply(post(member, 1, 0, true));
        service.apply(post(newcomer, 2, 0, true));
        assertThat(queries.summary(member).totalXp()).isEqualTo(20);
        assertThat(queries.summary(newcomer).totalXp()).isZero();
        service.apply(post(member, 3, 4, false));
        service.apply(post(member, 3, 0, true));
        assertThat(queries.summary(member).totalXp()).isEqualTo(20);
    }

    @Test
    void simultaneousDuplicatesAndDistinctActivitiesDoNotLoseUpdates() throws Exception {
        long user = user(true);
        freeze();
        ActivityEvent duplicate = post(user, 100, 100, true);
        List<Callable<Void>> work = new ArrayList<>();
        for (int i = 0; i < 12; i++)
            work.add(
                    () -> {
                        service.apply(duplicate);
                        return null;
                    });
        for (int i = 1; i <= 12; i++) {
            ActivityEvent event = post(user, i, i, true);
            work.add(
                    () -> {
                        service.apply(event);
                        return null;
                    });
        }
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (var future : executor.invokeAll(work)) future.get();
        }
        assertThat(queries.summary(user).totalXp()).isEqualTo(260);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM user_experience_history WHERE user_id = ?",
                                Long.class,
                                user))
                .isEqualTo(13);
        assertThat(operations.reconcile().get("ledger_mismatches")).hasToString("0");
    }

    @Test
    void rollbackIncludesHistoryAggregateGradeAndSummaryOutbox() {
        long user = user(true);
        freeze();
        assertThatThrownBy(
                        () ->
                                transactions.executeWithoutResult(
                                        status -> {
                                            service.apply(post(user, 1, 1, true));
                                            service.apply(post(user, 2, 2, true));
                                            service.apply(post(user, 3, 3, true));
                                            throw new IllegalStateException("synthetic rollback");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(queries.summary(user).totalXp()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_experience_history", Long.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT revision FROM user_info_outbox WHERE user_id = ?",
                                Long.class,
                                user))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT grade FROM users WHERE id = ?", String.class, user))
                .isEqualTo("APPRENTICE");
    }

    @Test
    void historyIsIsolatedAndCursorDoesNotDuplicateEntries() {
        long one = user(true);
        long two = user(true);
        freeze();
        for (int i = 1; i <= 3; i++) service.apply(post(one, i, i, true));
        service.apply(post(two, 9, 9, true));
        var first = queries.history(one, null, 2);
        var next = queries.history(one, first.nextCursor(), 2);
        assertThat(first.items()).hasSize(2);
        assertThat(next.items()).hasSize(1);
        assertThat(next.nextCursor()).isNull();
        assertThat(queries.history(two, null, 20).items()).hasSize(1);
        assertThatThrownBy(() -> queries.history(one, 0L, 20)).isInstanceOf(RuntimeException.class);
    }

    private long user(boolean verified) {
        long id =
                transactions.execute(
                        status -> {
                            User user =
                                    User.builder()
                                            .email(
                                                    "xp-it-"
                                                            + UUID.randomUUID()
                                                            + "@example.invalid")
                                            .nickname(
                                                    "xp-"
                                                            + UUID.randomUUID()
                                                                    .toString()
                                                                    .substring(0, 12))
                                            .status(Status.ACTIVE)
                                            .build();
                            if (verified) user.updateVerificationStatus(true, LocalDateTime.now());
                            return users.saveAndFlush(user).getId();
                        });
        if (verified) interval(id, Instant.now().minusSeconds(86400));
        return id;
    }

    private void freeze() {
        cutoff = operations.freeze().toInstant(ZoneOffset.UTC);
    }

    private void interval(long id, Instant start) {
        jdbc.update(
                "INSERT INTO user_verification_history(user_id, verified_at, verification_success, created_at, experience_valid_from) VALUES (?, ?, TRUE, ?, ?)",
                id,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.ofInstant(start, ZoneOffset.UTC));
    }

    private void verify(long id, Instant start) {
        jdbc.update(
                "UPDATE users SET is_verified = TRUE, grade = COALESCE(grade, 'APPRENTICE') WHERE id = ?",
                id);
        interval(id, start);
    }

    private void loseVerification(long id, Instant at) {
        jdbc.update("UPDATE users SET is_verified = FALSE WHERE id = ?", id);
        jdbc.update(
                "UPDATE user_verification_history SET valid_until = ? WHERE user_id = ? AND experience_valid_from IS NOT NULL AND valid_until IS NULL",
                LocalDateTime.ofInstant(at, ZoneOffset.UTC),
                id);
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
                revision == 0 ? cutoff.minusSeconds(86400) : cutoff.plusSeconds(revision));
    }

    private ActivityEvent like(long actor, long author, long revision, boolean active) {
        return new ActivityEvent(
                UUID.randomUUID().toString(),
                1,
                "POST:99:LIKE:" + actor,
                "LIKE",
                actor,
                author,
                "POST",
                99,
                "POST",
                99,
                active,
                revision,
                cutoff.plusSeconds(revision));
    }
}
