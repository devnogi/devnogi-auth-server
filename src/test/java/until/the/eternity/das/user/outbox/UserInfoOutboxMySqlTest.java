package until.the.eternity.das.user.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UserInfoOutboxDispatchService.class, UserInfoOutboxMySqlTest.Configuration.class})
@EnabledIfEnvironmentVariable(named = "NICKNAME_MYSQL_REQUIRED", matches = "true")
class UserInfoOutboxMySqlTest {
    private static final String EMAIL_PREFIX = "outbox-mysql-it-";
    private static final LocalDateTime EARLIEST = LocalDateTime.of(2000, 1, 1, 0, 0);

    @TestConfiguration
    static class Configuration {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("NICKNAME_MYSQL_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("NICKNAME_MYSQL_USER"));
        properties.add(
                "spring.datasource.password", () -> System.getenv("NICKNAME_MYSQL_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserInfoOutboxDispatchService dispatch;

    @AfterEach
    void removeSyntheticRows() {
        jdbc.update(
                "DELETE o FROM user_info_outbox o JOIN users u ON u.id=o.user_id WHERE u.email LIKE ?",
                EMAIL_PREFIX + "%");
        jdbc.update("DELETE FROM users WHERE email LIKE ?", EMAIL_PREFIX + "%");
    }

    @Test
    void mysqlClaimLeaseAndConditionalPublishPreserveNewerRevision() {
        long userId = createUser("claim");
        insertPending(userId, "revision-one", EARLIEST);

        ClaimedUserInfoUpdate first = dispatch.claimBatch(1, Duration.ofMinutes(1)).getFirst();
        assertThat(first.userId()).isEqualTo(userId);
        assertThat(first.revision()).isEqualTo(1);

        List<ClaimedUserInfoUpdate> competing = dispatch.claimBatch(10, Duration.ofMinutes(1));
        assertThat(competing).noneMatch(claim -> claim.userId().equals(userId));

        jdbc.update(
                "UPDATE user_info_outbox SET revision=2, nickname='revision-two' WHERE user_id=?",
                userId);
        assertThat(dispatch.markPublished(first)).isTrue();
        assertThat(
                        jdbc.queryForMap(
                                "SELECT revision, published_revision, nickname FROM user_info_outbox WHERE user_id=?",
                                userId))
                .containsEntry("revision", 2L)
                .containsEntry("published_revision", 1L)
                .containsEntry("nickname", "revision-two");

        ClaimedUserInfoUpdate stale =
                new ClaimedUserInfoUpdate(
                        userId, first.revision(), 0, "stale-claim-token", first.event());
        assertThat(dispatch.markPublished(stale)).isFalse();

        jdbc.update(
                "UPDATE user_info_outbox SET next_attempt_at=? WHERE user_id=?", EARLIEST, userId);
        ClaimedUserInfoUpdate second = dispatch.claimBatch(1, Duration.ofMinutes(1)).getFirst();
        assertThat(second.userId()).isEqualTo(userId);
        assertThat(second.revision()).isEqualTo(2);
        assertThat(second.event().nickname()).isEqualTo("revision-two");
    }

    @Test
    void mysqlInsertIgnoreBackfillIsLimitedAndDoesNotOverwriteExistingSnapshot() {
        UserInfoOutboxBackfill baseline =
                new UserInfoOutboxBackfill(jdbc, new SimpleMeterRegistry(), 100_000);
        baseline.backfillMissingUsers();
        long userId = createUser("backfill");
        UserInfoOutboxBackfill oneAtATime =
                new UserInfoOutboxBackfill(jdbc, new SimpleMeterRegistry(), 1);

        oneAtATime.backfillMissingUsers();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM user_info_outbox WHERE user_id=?",
                                Integer.class,
                                userId))
                .isEqualTo(1);

        jdbc.update(
                "UPDATE user_info_outbox SET revision=9, nickname='newer-snapshot' WHERE user_id=?",
                userId);
        oneAtATime.backfillMissingUsers();
        assertThat(
                        jdbc.queryForMap(
                                "SELECT revision, nickname FROM user_info_outbox WHERE user_id=?",
                                userId))
                .containsEntry("revision", 9L)
                .containsEntry("nickname", "newer-snapshot");
    }

    private long createUser(String suffix) {
        String email = EMAIL_PREFIX + suffix + "-" + UUID.randomUUID() + "@example.invalid";
        jdbc.update(
                "INSERT INTO users (email, nickname, status, is_verified) VALUES (?, ?, 'ACTIVE', FALSE)",
                email,
                "synthetic-" + suffix);
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
    }

    private void insertPending(long userId, String nickname, LocalDateTime nextAttemptAt) {
        jdbc.update(
                """
                INSERT INTO user_info_outbox (
                    user_id, revision, published_revision, nickname, verified,
                    attempt_count, next_attempt_at, pending_since, created_at, updated_at
                ) VALUES (?, 1, 0, ?, FALSE, 0, ?, UTC_TIMESTAMP(6),
                          UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                userId,
                nickname,
                nextAttemptAt);
    }
}
