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
import until.the.eternity.das.experience.ActivityGrade;
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
        // merge() can retain a detached rank in memory even though ORM cannot update the
        // grade column. Snapshot the persisted rank after the lock, not that stale value.
        entityManager.flush();
        String persistedGrade =
                (String)
                        entityManager
                                .createNativeQuery(
                                        "SELECT grade FROM users WHERE id = :id FOR UPDATE")
                                .setParameter("id", user.getId())
                                .getSingleResult();
        user.updateActivityGrade(
                persistedGrade == null ? null : ActivityGrade.valueOf(persistedGrade));
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<UserInfoOutbox> existing = outboxes.findByUserIdForUpdate(user.getId());
        UserInfoOutbox outbox = existing.orElseGet(() -> UserInfoOutbox.first(user, now));
        existing.ifPresent(value -> value.replaceWith(user, now));
        var arcanas =
                entityManager
                        .createNativeQuery(
                                "SELECT arcana FROM user_game_profile WHERE user_id = :id FOR UPDATE",
                                String.class)
                        .setParameter("id", user.getId())
                        .getResultList();
        outbox.updateArcana(arcanas.isEmpty() ? null : (String) arcanas.getFirst());
        outboxes.save(outbox);
        return outbox.getRevision();
    }
}
