package until.the.eternity.das.user.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class UserInfoOutboxMonitorTest {
    @Mock private UserInfoOutboxRepository outboxes;
    private SimpleMeterRegistry metrics;
    private Clock clock;

    @BeforeEach
    void setUp() {
        metrics = new SimpleMeterRegistry();
        clock = Clock.fixed(Instant.parse("2026-11-05T09:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void publishesCurrentBacklogGaugesAndSafeWarning(CapturedOutput output) {
        when(outboxes.countPending()).thenReturn(12L);
        when(outboxes.findOldestPendingSince()).thenReturn(LocalDateTime.of(2026, 11, 5, 8, 58));
        when(outboxes.findMaxPendingAttemptCount()).thenReturn(4);
        UserInfoOutboxMonitor monitor = monitor(10, 100, 60, 600, 3, 10);

        monitor.observe();

        assertThat(gauge(UserInfoOutboxMonitor.PENDING_GAUGE)).isEqualTo(12);
        assertThat(gauge(UserInfoOutboxMonitor.OLDEST_AGE_GAUGE)).isEqualTo(120);
        assertThat(gauge(UserInfoOutboxMonitor.MAX_ATTEMPTS_GAUGE)).isEqualTo(4);
        assertThat(output).contains("backlog is elevated");
        assertThat(output).contains("pendingCount=12, oldestAgeSeconds=120, maxAttempts=4");
        assertThat(output).doesNotContain("nickname", "profileImageUrl", "email");
    }

    @Test
    void emitsErrorAtInterventionThresholdAndZerosEmptyBacklog(CapturedOutput output) {
        when(outboxes.countPending()).thenReturn(1000L, 0L);
        when(outboxes.findOldestPendingSince())
                .thenReturn(LocalDateTime.of(2026, 11, 5, 8, 0), (LocalDateTime) null);
        when(outboxes.findMaxPendingAttemptCount()).thenReturn(10, (Integer) null);
        UserInfoOutboxMonitor monitor = monitor(100, 1000, 300, 1800, 3, 10);

        monitor.observe();
        assertThat(output).contains("backlog requires intervention");
        monitor.observe();

        assertThat(gauge(UserInfoOutboxMonitor.PENDING_GAUGE)).isZero();
        assertThat(gauge(UserInfoOutboxMonitor.OLDEST_AGE_GAUGE)).isZero();
        assertThat(gauge(UserInfoOutboxMonitor.MAX_ATTEMPTS_GAUGE)).isZero();
    }

    private UserInfoOutboxMonitor monitor(
            long warnPending,
            long errorPending,
            long warnAge,
            long errorAge,
            int warnAttempts,
            int errorAttempts) {
        return new UserInfoOutboxMonitor(
                outboxes,
                clock,
                metrics,
                warnPending,
                errorPending,
                warnAge,
                errorAge,
                warnAttempts,
                errorAttempts);
    }

    private double gauge(String name) {
        return metrics.get(name).gauge().value();
    }
}
