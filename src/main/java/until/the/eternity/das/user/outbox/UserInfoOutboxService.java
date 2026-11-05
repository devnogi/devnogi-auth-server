package until.the.eternity.das.user.outbox;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import until.the.eternity.das.user.entity.User;

@Service
@RequiredArgsConstructor
public class UserInfoOutboxService {
    private final UserInfoOutboxRepository outboxes;
    private final Clock clock;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public long enqueue(User user) {
        if (user.getId() == null) {
            throw new IllegalArgumentException("A persisted user is required");
        }
        entityManager.lock(user, LockModeType.PESSIMISTIC_WRITE);
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<UserInfoOutbox> existing = outboxes.findByUserIdForUpdate(user.getId());
        UserInfoOutbox outbox = existing.orElseGet(() -> UserInfoOutbox.first(user, now));
        existing.ifPresent(value -> value.replaceWith(user, now));
        outboxes.save(outbox);
        return outbox.getRevision();
    }
}
