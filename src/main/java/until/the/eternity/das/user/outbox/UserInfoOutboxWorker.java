package until.the.eternity.das.user.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import until.the.eternity.das.common.application.KafkaProducerService;

@Slf4j
@Component
public class UserInfoOutboxWorker {
    private final UserInfoOutboxDispatchService dispatch;
    private final KafkaProducerService producer;
    private final MeterRegistry metrics;
    private final int batchSize;
    private final Duration leaseDuration;
    private final Duration baseBackoff;
    private final Duration maxBackoff;

    public UserInfoOutboxWorker(
            UserInfoOutboxDispatchService dispatch,
            KafkaProducerService producer,
            MeterRegistry metrics,
            @Value("${app.user-info-outbox.batch-size:50}") int batchSize,
            @Value("${app.user-info-outbox.lease-duration:PT1M}") Duration leaseDuration,
            @Value("${app.user-info-outbox.base-backoff:PT5S}") Duration baseBackoff,
            @Value("${app.user-info-outbox.max-backoff:PT10M}") Duration maxBackoff) {
        this.dispatch = dispatch;
        this.producer = producer;
        this.metrics = metrics;
        this.batchSize = batchSize;
        this.leaseDuration = leaseDuration;
        this.baseBackoff = baseBackoff;
        this.maxBackoff = maxBackoff;
    }

    @Scheduled(fixedDelayString = "${app.user-info-outbox.poll-interval-ms:1000}")
    public void publishPending() {
        for (ClaimedUserInfoUpdate claim : dispatch.claimBatch(batchSize, leaseDuration)) {
            publish(claim);
        }
    }

    void publish(ClaimedUserInfoUpdate claim) {
        try {
            producer.sendUserInfoUpdateEvent(String.valueOf(claim.userId()), claim.event())
                    .whenComplete(
                            (ignored, failure) -> {
                                if (failure == null) {
                                    try {
                                        if (dispatch.markPublished(claim)) {
                                            metrics.counter(
                                                            "memonogi.user.info.outbox.publish.success")
                                                    .increment();
                                        }
                                    } catch (RuntimeException acknowledgementFailure) {
                                        recordAcknowledgementFailure(claim, acknowledgementFailure);
                                    }
                                    return;
                                }
                                recordFailure(claim, failure);
                            });
        } catch (RuntimeException failure) {
            recordFailure(claim, failure);
        }
    }

    private void recordFailure(ClaimedUserInfoUpdate claim, Throwable failure) {
        Duration backoff = retryBackoff(claim.attemptCount());
        try {
            if (dispatch.markFailed(claim, backoff)) {
                metrics.counter("memonogi.user.info.outbox.publish.failure").increment();
                log.warn(
                        "User summary publication failed. userId={}, revision={}, retryInSeconds={}, exceptionType={}",
                        claim.userId(),
                        claim.revision(),
                        backoff.toSeconds(),
                        failure.getClass().getSimpleName());
            }
        } catch (RuntimeException retrySchedulingFailure) {
            recordRetrySchedulingFailure(claim, retrySchedulingFailure);
        }
    }

    private void recordAcknowledgementFailure(
            ClaimedUserInfoUpdate claim, RuntimeException failure) {
        metrics.counter("memonogi.user.info.outbox.publish.acknowledgement.failure").increment();
        log.error(
                "User summary publication acknowledgement failed; the claim lease will be retried. userId={}, revision={}, exceptionType={}",
                claim.userId(),
                claim.revision(),
                failure.getClass().getSimpleName());
    }

    private void recordRetrySchedulingFailure(
            ClaimedUserInfoUpdate claim, RuntimeException failure) {
        metrics.counter("memonogi.user.info.outbox.retry.scheduling.failure").increment();
        log.error(
                "User summary retry scheduling failed; the claim lease will be retried. userId={}, revision={}, exceptionType={}",
                claim.userId(),
                claim.revision(),
                failure.getClass().getSimpleName());
    }

    Duration retryBackoff(int attemptCount) {
        int exponent = Math.min(Math.max(attemptCount, 0), 20);
        long multiplier = 1L << exponent;
        long seconds = Math.min(maxBackoff.toSeconds(), baseBackoff.toSeconds() * multiplier);
        return Duration.ofSeconds(Math.max(1, seconds));
    }
}
