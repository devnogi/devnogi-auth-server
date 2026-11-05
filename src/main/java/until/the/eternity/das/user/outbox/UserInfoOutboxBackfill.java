package until.the.eternity.das.user.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.user-info-outbox.backfill-enabled", havingValue = "true")
public class UserInfoOutboxBackfill {
    private final JdbcTemplate jdbc;
    private final MeterRegistry metrics;

    @Value("${app.user-info-outbox.backfill-batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.user-info-outbox.backfill-interval-ms:60000}")
    @Transactional
    public void backfillMissingUsers() {
        int inserted =
                jdbc.update(
                        """
                        INSERT IGNORE INTO user_info_outbox (
                            user_id, revision, published_revision, nickname,
                            profile_image_url, server_name, verified,
                            attempt_count, next_attempt_at, created_at, updated_at
                        )
                        SELECT u.id, 1, 0, u.nickname,
                               u.profile_image_url, u.server_name, u.is_verified,
                               0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
                        FROM users u
                        LEFT JOIN user_info_outbox o ON o.user_id = u.id
                        WHERE o.user_id IS NULL
                        ORDER BY u.id
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED
                        """,
                        batchSize);
        if (inserted > 0) {
            metrics.counter("memonogi.user.info.outbox.backfill.inserted").increment(inserted);
            log.info("Queued user summary backfill batch. inserted={}", inserted);
        }
    }
}
