package until.the.eternity.das.user.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;
import until.the.eternity.das.user.entity.enums.Status;

@DataJpaTest
@Import({
    UserInfoOutboxService.class,
    UserInfoOutboxDispatchService.class,
    UserInfoOutboxJpaTest.Configuration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserInfoOutboxJpaTest {
    @TestConfiguration
    static class Configuration {
        @Bean
        MutableClock clock() {
            return new MutableClock(Instant.parse("2026-11-05T00:00:00Z"));
        }
    }

    static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        void reset() {
            instant = Instant.parse("2026-11-05T00:00:00Z");
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @Autowired private UserRepository users;
    @Autowired private UserInfoOutboxRepository outboxes;
    @Autowired private UserInfoOutboxService service;
    @Autowired private UserInfoOutboxDispatchService dispatch;
    @Autowired private TransactionTemplate transactions;
    @Autowired private MutableClock clock;

    @BeforeEach
    void cleanDatabase() {
        transactions.executeWithoutResult(
                status -> {
                    outboxes.deleteAll();
                    users.deleteAll();
                });
        clock.reset();
    }

    @Test
    void userAndPendingSnapshotRollbackAtomically() {
        String email = "outbox-rollback-" + UUID.randomUUID() + "@example.invalid";

        transactions.executeWithoutResult(
                status -> {
                    User user = users.saveAndFlush(user(email, "rollback-name"));
                    service.enqueue(user);
                    status.setRollbackOnly();
                });

        assertThat(users.findByEmail(email)).isEmpty();
        assertThat(outboxes.findAll()).isEmpty();
    }

    @Test
    void committedUserHasPendingRevisionOneSnapshot() {
        String email = "outbox-commit-" + UUID.randomUUID() + "@example.invalid";

        Long userId =
                transactions.execute(
                        status -> {
                            User user = users.saveAndFlush(user(email, "commit-name"));
                            service.enqueue(user);
                            return user.getId();
                        });

        UserInfoOutbox saved = outboxes.findById(userId).orElseThrow();
        assertThat(saved.getRevision()).isEqualTo(1);
        assertThat(saved.getPublishedRevision()).isZero();
        assertThat(saved.getNickname()).isEqualTo("commit-name");
    }

    @Test
    void claimLeasePreventsDuplicateUntilExpiry() {
        Long userId = createPendingUser("lease-name");

        ClaimedUserInfoUpdate first = dispatch.claimBatch(1, Duration.ofMinutes(1)).getFirst();
        assertThat(first.userId()).isEqualTo(userId);
        assertThat(dispatch.claimBatch(1, Duration.ofMinutes(1))).isEmpty();

        clock.advance(Duration.ofSeconds(61));
        ClaimedUserInfoUpdate reclaimed = dispatch.claimBatch(1, Duration.ofMinutes(1)).getFirst();
        assertThat(reclaimed.userId()).isEqualTo(userId);
        assertThat(reclaimed.claimToken()).isNotEqualTo(first.claimToken());
        assertThat(dispatch.markPublished(first)).isFalse();
        assertThat(dispatch.markPublished(reclaimed)).isTrue();
    }

    @Test
    void publishingOlderClaimDoesNotLoseNewerSnapshot() {
        Long userId = createPendingUser("revision-one");
        ClaimedUserInfoUpdate first = dispatch.claimBatch(1, Duration.ofMinutes(1)).getFirst();

        transactions.executeWithoutResult(
                status -> {
                    User user = users.findById(userId).orElseThrow();
                    user.updateUserInfo("revision-two", null);
                    service.enqueue(user);
                });

        assertThat(dispatch.markPublished(first)).isTrue();
        UserInfoOutbox afterFirst = outboxes.findById(userId).orElseThrow();
        assertThat(afterFirst.getRevision()).isEqualTo(2);
        assertThat(afterFirst.getPublishedRevision()).isEqualTo(1);
        assertThat(afterFirst.getNickname()).isEqualTo("revision-two");

        ClaimedUserInfoUpdate second = dispatch.claimBatch(1, Duration.ofMinutes(1)).getFirst();
        assertThat(second.revision()).isEqualTo(2);
        assertThat(second.event().nickname()).isEqualTo("revision-two");
        assertThat(dispatch.markPublished(second)).isTrue();
        assertThat(dispatch.markPublished(second)).isFalse();
    }

    @Test
    void failedClaimBacksOffBeforeRetry() {
        Long userId = createPendingUser("retry-name");
        ClaimedUserInfoUpdate claim = dispatch.claimBatch(1, Duration.ofMinutes(1)).getFirst();

        assertThat(dispatch.markFailed(claim, Duration.ofSeconds(5))).isTrue();
        assertThat(dispatch.claimBatch(1, Duration.ofMinutes(1))).isEmpty();
        clock.advance(Duration.ofSeconds(5));
        ClaimedUserInfoUpdate retry = dispatch.claimBatch(1, Duration.ofMinutes(1)).getFirst();
        assertThat(retry.userId()).isEqualTo(userId);
        assertThat(retry.attemptCount()).isEqualTo(1);
    }

    private Long createPendingUser(String nickname) {
        String email = "outbox-" + UUID.randomUUID() + "@example.invalid";
        return transactions.execute(
                status -> {
                    User user = users.saveAndFlush(user(email, nickname));
                    service.enqueue(user);
                    return user.getId();
                });
    }

    private User user(String email, String nickname) {
        return User.builder()
                .email(email)
                .nickname(nickname)
                .status(Status.ACTIVE)
                .verified(false)
                .build();
    }
}
