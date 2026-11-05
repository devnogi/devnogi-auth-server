package until.the.eternity.das.user.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class UserInfoOutboxMonitor {
    static final String PENDING_GAUGE = "memonogi.user.info.outbox.pending.current";
    static final String OLDEST_AGE_GAUGE = "memonogi.user.info.outbox.oldest.age.seconds";
    static final String MAX_ATTEMPTS_GAUGE = "memonogi.user.info.outbox.attempts.max";

    private final UserInfoOutboxRepository outboxes;
    private final Clock clock;
    private final long warnPendingCount;
    private final long errorPendingCount;
    private final long warnOldestAgeSeconds;
    private final long errorOldestAgeSeconds;
    private final int warnMaxAttempts;
    private final int errorMaxAttempts;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();
    private final AtomicLong maxAttempts = new AtomicLong();

    public UserInfoOutboxMonitor(
            UserInfoOutboxRepository outboxes,
            Clock clock,
            MeterRegistry metrics,
            @Value("${app.user-info-outbox.observation.warn-pending-count:100}")
                    long warnPendingCount,
            @Value("${app.user-info-outbox.observation.error-pending-count:1000}")
                    long errorPendingCount,
            @Value("${app.user-info-outbox.observation.warn-oldest-age-seconds:300}")
                    long warnOldestAgeSeconds,
            @Value("${app.user-info-outbox.observation.error-oldest-age-seconds:1800}")
                    long errorOldestAgeSeconds,
            @Value("${app.user-info-outbox.observation.warn-max-attempts:3}") int warnMaxAttempts,
            @Value("${app.user-info-outbox.observation.error-max-attempts:10}")
                    int errorMaxAttempts) {
        this.outboxes = outboxes;
        this.clock = clock;
        this.warnPendingCount = warnPendingCount;
        this.errorPendingCount = errorPendingCount;
        this.warnOldestAgeSeconds = warnOldestAgeSeconds;
        this.errorOldestAgeSeconds = errorOldestAgeSeconds;
        this.warnMaxAttempts = warnMaxAttempts;
        this.errorMaxAttempts = errorMaxAttempts;
        metrics.gauge(PENDING_GAUGE, pending);
        metrics.gauge(OLDEST_AGE_GAUGE, oldestAgeSeconds);
        metrics.gauge(MAX_ATTEMPTS_GAUGE, maxAttempts);
    }

    @Scheduled(fixedDelayString = "${app.user-info-outbox.observation.interval-ms:30000}")
    @Transactional(readOnly = true)
    public void observe() {
        long currentPending = outboxes.countPending();
        LocalDateTime oldestPendingSince = outboxes.findOldestPendingSince();
        int currentMaxAttempts = valueOrZero(outboxes.findMaxPendingAttemptCount());
        long currentOldestAge = ageSeconds(oldestPendingSince);

        pending.set(currentPending);
        oldestAgeSeconds.set(currentOldestAge);
        maxAttempts.set(currentMaxAttempts);

        if (currentPending >= errorPendingCount
                || currentOldestAge >= errorOldestAgeSeconds
                || currentMaxAttempts >= errorMaxAttempts) {
            log.error(
                    "User info outbox backlog requires intervention. pendingCount={}, oldestAgeSeconds={}, maxAttempts={}",
                    currentPending,
                    currentOldestAge,
                    currentMaxAttempts);
        } else if (currentPending >= warnPendingCount
                || currentOldestAge >= warnOldestAgeSeconds
                || currentMaxAttempts >= warnMaxAttempts) {
            log.warn(
                    "User info outbox backlog is elevated. pendingCount={}, oldestAgeSeconds={}, maxAttempts={}",
                    currentPending,
                    currentOldestAge,
                    currentMaxAttempts);
        }
    }

    private long ageSeconds(LocalDateTime oldestPendingSince) {
        if (oldestPendingSince == null) return 0;
        return Math.max(
                0, Duration.between(oldestPendingSince, LocalDateTime.now(clock)).toSeconds());
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
