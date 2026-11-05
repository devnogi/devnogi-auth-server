package until.the.eternity.das.user.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserInfoOutboxDispatchService {
    private final UserInfoOutboxRepository outboxes;
    private final Clock clock;

    @Transactional
    public List<ClaimedUserInfoUpdate> claimBatch(int batchSize, Duration leaseDuration) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime claimUntil = now.plus(leaseDuration);
        return outboxes.findClaimable(now, batchSize).stream()
                .map(outbox -> outbox.claim(UUID.randomUUID().toString(), claimUntil, now))
                .toList();
    }

    @Transactional
    public boolean markPublished(ClaimedUserInfoUpdate claim) {
        LocalDateTime now = LocalDateTime.now(clock);
        return outboxes.markPublished(claim.userId(), claim.revision(), claim.claimToken(), now)
                == 1;
    }

    @Transactional
    public boolean markFailed(ClaimedUserInfoUpdate claim, Duration backoff) {
        LocalDateTime now = LocalDateTime.now(clock);
        return outboxes.markFailed(claim.userId(), claim.claimToken(), now.plus(backoff), now) == 1;
    }
}
