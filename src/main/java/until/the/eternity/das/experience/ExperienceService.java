package until.the.eternity.das.experience;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.enums.Status;
import until.the.eternity.das.user.outbox.UserInfoOutboxService;

/** One ledger transaction for an activity state, including both sides of a like. */
@Service
@RequiredArgsConstructor
public class ExperienceService {
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final UserInfoOutboxService summaries;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void apply(ActivityEvent event) {
        event.validate();
        LocalDateTime rollout = rolloutAt();
        List<Recipient> recipients = recipients(event);
        // Same lock order as verification ownership transfer. No outbox locks before user locks.
        for (Recipient recipient : recipients) {
            if (entityManager.find(User.class, recipient.userId(), LockModeType.PESSIMISTIC_WRITE)
                    == null) {
                throw new IllegalArgumentException("Activity refers to an unknown user");
            }
        }
        for (Recipient recipient : recipients) applyRecipient(event, recipient, rollout);
    }

    private List<Recipient> recipients(ActivityEvent event) {
        if (!"LIKE".equals(event.activityType())) {
            return List.of(new Recipient(event.actorUserId(), event.activityType()));
        }
        if (event.actorUserId() == event.authorUserId()) return List.of();
        return List.of(
                        new Recipient(event.actorUserId(), "LIKE_GIVEN"),
                        new Recipient(event.authorUserId(), "LIKE_RECEIVED"))
                .stream()
                .sorted(Comparator.comparingLong(Recipient::userId))
                .toList();
    }

    private void applyRecipient(ActivityEvent event, Recipient recipient, LocalDateTime rollout) {
        User user = entityManager.find(User.class, recipient.userId());
        List<State> states =
                jdbc.query(
                        "SELECT source_revision, activity_balance FROM user_experience_history "
                                + "WHERE user_id = ? AND activity_key = ? AND activity_code = ? "
                                + "ORDER BY source_revision DESC LIMIT 1",
                        (rs, row) -> new State(rs.getLong(1), rs.getLong(2)),
                        user.getId(),
                        event.activityKey(),
                        recipient.code());
        if (!states.isEmpty() && states.getFirst().revision() >= event.sourceRevision()) return;
        if (Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM user_experience_history WHERE event_id = ? "
                                + "AND user_id = ? AND activity_code = ?)",
                        Boolean.class,
                        event.eventId(),
                        user.getId(),
                        recipient.code()))) return;

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDateTime occurred = LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC);
        jdbc.update(
                "INSERT IGNORE INTO user_experience(user_id, total_xp, backfill_eligible, updated_at) VALUES (?, 0, FALSE, ?)",
                user.getId(),
                now);
        Balance balance =
                jdbc.queryForObject(
                        "SELECT total_xp, backfill_eligible FROM user_experience WHERE user_id = ?",
                        (rs, row) -> new Balance(rs.getLong(1), rs.getBoolean(2)),
                        user.getId());
        Policy policy =
                jdbc.queryForObject(
                        "SELECT xp, enabled FROM experience_activity WHERE code = ? AND policy_version = 1",
                        (rs, row) -> new Policy(rs.getLong(1), rs.getBoolean(2)),
                        recipient.code());
        if (balance == null || policy == null)
            throw new IllegalStateException("Missing experience policy or balance");
        boolean historical = event.sourceRevision() == 0 || occurred.isBefore(rollout);
        boolean eligible = historical ? balance.backfillEligible() : verifiedAt(user, occurred);
        long oldBalance = states.isEmpty() ? 0 : states.getFirst().balance();
        long newBalance = event.active() && policy.enabled() && eligible ? policy.xp() : 0;
        long delta = Math.subtractExact(newBalance, oldBalance);
        long total = Math.addExact(balance.total(), delta);
        if (total < 0) throw new IllegalStateException("Experience ledger invariant violated");

        jdbc.update(
                """
                INSERT INTO user_experience_history
                (user_id, activity_code, policy_version, policy_xp, activity_key, event_id,
                 source_revision, target_type, target_id, root_target_type, root_target_id,
                 active, delta_xp, activity_balance, total_xp, occurred_at, processed_at)
                VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                user.getId(),
                recipient.code(),
                policy.xp(),
                event.activityKey(),
                event.eventId(),
                event.sourceRevision(),
                event.targetType(),
                event.targetId(),
                event.rootTargetType(),
                event.rootTargetId(),
                event.active(),
                delta,
                newBalance,
                total,
                occurred,
                now);
        jdbc.update(
                """
                UPDATE user_experience SET total_xp = ?, updated_at = ?,
                last_backfill_at = CASE WHEN ? THEN ? ELSE last_backfill_at END WHERE user_id = ?
                """,
                total,
                now,
                historical,
                now,
                user.getId());
        // Never-verified users retain NULL. Reversals still update a previously initialized grade.
        if (user.getGrade() != null || user.isVerified() || total > 0) {
            ActivityGrade grade = ActivityGrade.fromXp(total);
            if (grade != user.getGrade()) {
                jdbc.update("UPDATE users SET grade = ? WHERE id = ?", grade.name(), user.getId());
                user.updateActivityGrade(grade);
                summaries.enqueue(user);
            }
        }
    }

    private boolean verifiedAt(User user, LocalDateTime occurred) {
        if (user.getStatus() != Status.ACTIVE) return false;
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
                SELECT EXISTS(SELECT 1 FROM user_verification_history
                WHERE user_id = ? AND verification_success = TRUE
                AND experience_valid_from <= ? AND (valid_until IS NULL OR valid_until > ?))
                """,
                        Boolean.class,
                        user.getId(),
                        occurred,
                        occurred));
    }

    public LocalDateTime rolloutAt() {
        LocalDateTime timestamp =
                jdbc.queryForObject(
                        "SELECT rollout_at FROM experience_activity WHERE code = 'POST_PUBLISHED' AND policy_version = 1",
                        (rs, row) -> rs.getObject(1, LocalDateTime.class));
        if (timestamp == null)
            throw new IllegalStateException(
                    "Freeze the experience cohort before consuming activities");
        return timestamp;
    }

    private record Recipient(long userId, String code) {}

    private record State(long revision, long balance) {}

    private record Balance(long total, boolean backfillEligible) {}

    private record Policy(long xp, boolean enabled) {}
}
