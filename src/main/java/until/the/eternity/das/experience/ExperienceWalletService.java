package until.the.eternity.das.experience;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.outbox.UserInfoOutboxService;

/** Activity history remains immutable; advertising consumption never rewrites earned XP. */
@Service
@RequiredArgsConstructor
public class ExperienceWalletService {
    private final JdbcTemplate jdbc;
    private final UserInfoOutboxService summaries;

    @Transactional(readOnly = true)
    public Snapshot snapshot(long userId) {
        return jdbc.queryForObject(
                """
                SELECT COALESCE((SELECT total_xp FROM user_experience WHERE user_id = ?), 0),
                  COALESCE((SELECT SUM(amount) FROM hero_charge WHERE user_id = ?), 0),
                  COALESCE((SELECT SUM(amount) FROM hero_reservation WHERE user_id = ?), 0)
                """,
                (rs, row) -> balance(rs.getLong(1), rs.getLong(2), rs.getLong(3)),
                userId,
                userId,
                userId);
    }

    static Snapshot balance(long activity, long spent, long reserved) {
        if (activity < 0 || spent < 0 || reserved < 0) {
            throw new IllegalStateException("Experience wallet invariant violated");
        }
        long net = Math.subtractExact(activity, spent);
        long current = Math.max(0, net);
        return new Snapshot(
                activity,
                current,
                spent,
                reserved,
                Math.max(0, current - reserved),
                Math.max(0, -net));
    }

    /** Caller holds the user's write lock; grade and summary event join its transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recomputeGrade(User user) {
        Snapshot balance = snapshot(user.getId());
        if (user.getGrade() == null && !user.isVerified() && balance.activityXp() == 0) return;
        ActivityGrade grade = ActivityGrade.fromXp(balance.currentXp());
        if (grade == user.getGrade()) return;
        jdbc.update("UPDATE users SET grade = ? WHERE id = ?", grade.name(), user.getId());
        user.updateActivityGrade(grade);
        summaries.enqueue(user);
    }

    public record Snapshot(
            long activityXp,
            long currentXp,
            long spentXp,
            long reservedXp,
            long availableXp,
            long deficitXp) {}
}
