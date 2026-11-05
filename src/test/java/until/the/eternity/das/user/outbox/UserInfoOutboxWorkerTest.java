package until.the.eternity.das.user.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import until.the.eternity.das.common.application.KafkaProducerService;
import until.the.eternity.das.user.dto.response.UserInfoUpdateEvent;

@ExtendWith(OutputCaptureExtension.class)
class UserInfoOutboxWorkerTest {
    UserInfoOutboxDispatchService dispatch;
    KafkaProducerService producer;
    SimpleMeterRegistry metrics;
    UserInfoOutboxWorker worker;

    @BeforeEach
    void setUp() {
        dispatch = mock(UserInfoOutboxDispatchService.class);
        producer = mock(KafkaProducerService.class);
        metrics = new SimpleMeterRegistry();
        worker =
                new UserInfoOutboxWorker(
                        dispatch,
                        producer,
                        metrics,
                        10,
                        Duration.ofMinutes(1),
                        Duration.ofSeconds(5),
                        Duration.ofMinutes(10));
    }

    @Test
    void acknowledgedKafkaSendMarksOnlyClaimedRevisionPublished() {
        ClaimedUserInfoUpdate claim = claim(3L, 0);
        when(producer.sendUserInfoUpdateEvent("42", claim.event()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(dispatch.markPublished(claim)).thenReturn(true);

        worker.publish(claim);

        verify(dispatch).markPublished(claim);
        assertEquals(1.0, metrics.counter("memonogi.user.info.outbox.publish.success").count());
    }

    @Test
    void asynchronousFailureReleasesClaimWithBackoff() {
        ClaimedUserInfoUpdate claim = claim(3L, 2);
        when(producer.sendUserInfoUpdateEvent("42", claim.event()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("synthetic")));
        when(dispatch.markFailed(claim, Duration.ofSeconds(20))).thenReturn(true);

        worker.publish(claim);

        verify(dispatch).markFailed(claim, Duration.ofSeconds(20));
        assertEquals(1.0, metrics.counter("memonogi.user.info.outbox.publish.failure").count());
    }

    @Test
    void asynchronousFailureSchedulingDatabaseErrorIsObservableAndKeepsLease(
            CapturedOutput output) {
        ClaimedUserInfoUpdate claim = claim(3L, 2);
        when(producer.sendUserInfoUpdateEvent("42", claim.event()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("kafka")));
        doThrow(new IllegalArgumentException("synthetic database failure"))
                .when(dispatch)
                .markFailed(claim, Duration.ofSeconds(20));

        worker.publish(claim);

        assertRetrySchedulingFailureIsSafeAndObservable(output);
    }

    @Test
    void acknowledgementDatabaseFailureIsObservableAndKeepsLease(CapturedOutput output) {
        ClaimedUserInfoUpdate claim = claim(3L, 0);
        when(producer.sendUserInfoUpdateEvent("42", claim.event()))
                .thenReturn(CompletableFuture.completedFuture(null));
        doThrow(new IllegalStateException("synthetic database failure"))
                .when(dispatch)
                .markPublished(claim);

        worker.publish(claim);

        verify(dispatch, never()).markFailed(claim, Duration.ofSeconds(5));
        assertEquals(
                1.0,
                metrics.counter("memonogi.user.info.outbox.publish.acknowledgement.failure")
                        .count());
        assertEquals(0.0, metrics.counter("memonogi.user.info.outbox.publish.failure").count());
        org.assertj.core.api.Assertions.assertThat(output)
                .contains("claim lease will be retried")
                .contains("userId=42, revision=3, exceptionType=IllegalStateException")
                .doesNotContain("nickname", "synthetic database failure");
    }

    @Test
    void synchronousFailureAlsoReleasesClaim() {
        ClaimedUserInfoUpdate claim = claim(3L, 0);
        doThrow(new IllegalStateException("synthetic"))
                .when(producer)
                .sendUserInfoUpdateEvent("42", claim.event());
        when(dispatch.markFailed(claim, Duration.ofSeconds(5))).thenReturn(true);

        worker.publish(claim);

        verify(dispatch).markFailed(claim, Duration.ofSeconds(5));
    }

    @Test
    void synchronousFailureSchedulingDatabaseErrorIsObservableAndKeepsLease(CapturedOutput output) {
        ClaimedUserInfoUpdate claim = claim(3L, 0);
        doThrow(new IllegalStateException("kafka"))
                .when(producer)
                .sendUserInfoUpdateEvent("42", claim.event());
        doThrow(new IllegalArgumentException("synthetic database failure"))
                .when(dispatch)
                .markFailed(claim, Duration.ofSeconds(5));

        worker.publish(claim);

        assertRetrySchedulingFailureIsSafeAndObservable(output);
    }

    @Test
    void backoffIsBounded() {
        assertEquals(Duration.ofMinutes(10), worker.retryBackoff(20));
    }

    private void assertRetrySchedulingFailureIsSafeAndObservable(CapturedOutput output) {
        assertEquals(
                1.0, metrics.counter("memonogi.user.info.outbox.retry.scheduling.failure").count());
        assertEquals(0.0, metrics.counter("memonogi.user.info.outbox.publish.failure").count());
        org.assertj.core.api.Assertions.assertThat(output)
                .contains("retry scheduling failed; the claim lease will be retried")
                .contains("userId=42, revision=3, exceptionType=IllegalArgumentException")
                .doesNotContain("nickname", "synthetic database failure", "kafka");
    }

    private ClaimedUserInfoUpdate claim(long revision, int attempt) {
        return new ClaimedUserInfoUpdate(
                42L,
                revision,
                attempt,
                "claim-token",
                new UserInfoUpdateEvent(42L, "nickname", null, null, false, revision));
    }
}
