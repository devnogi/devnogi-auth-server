package until.the.eternity.das.experience;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;
import until.the.eternity.das.user.entity.enums.Status;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExperienceQueryService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;

    public Summary summary(Long userId) {
        User user = requireUser(userId);
        List<Long> totals =
                jdbc.query(
                        "SELECT total_xp FROM user_experience WHERE user_id = ?",
                        (rs, row) -> rs.getLong(1),
                        userId);
        long total = totals.isEmpty() ? 0 : totals.getFirst();
        ActivityGrade grade = ActivityGrade.fromXp(total);
        ActivityGrade next = grade.next();
        boolean eligible = user.isVerified() && user.getStatus() == Status.ACTIVE;
        double progress =
                next == null
                        ? 100
                        : 100.0 * (total - grade.minXp()) / (next.minXp() - grade.minXp());
        return new Summary(
                eligible,
                eligible ? grade : null,
                total,
                grade.minXp(),
                next,
                next == null ? null : next.minXp(),
                next == null ? 0 : next.minXp() - total,
                progress,
                ActivityGrade.thresholds());
    }

    public History history(Long userId, Long cursor, int size) {
        requireUser(userId);
        if (size < 1 || size > 100 || (cursor != null && cursor < 1)) {
            throw new CustomException(GlobalExceptionCode.INVALID_EXPERIENCE_CURSOR);
        }
        List<HistoryItem> rows =
                jdbc.query(
                        """
                SELECT h.id, h.activity_code, p.name, h.delta_xp, h.total_xp,
                       h.occurred_at, h.processed_at
                FROM user_experience_history h JOIN experience_activity p
                  ON p.code = h.activity_code AND p.policy_version = h.policy_version
                WHERE h.user_id = ? AND h.id < ? ORDER BY h.id DESC LIMIT ?
                """,
                        (rs, row) ->
                                new HistoryItem(
                                        rs.getLong(1),
                                        rs.getString(2),
                                        rs.getString(3),
                                        rs.getLong(4),
                                        rs.getLong(5),
                                        rs.getObject(6, LocalDateTime.class)
                                                .toInstant(ZoneOffset.UTC),
                                        rs.getObject(7, LocalDateTime.class)
                                                .toInstant(ZoneOffset.UTC)),
                        userId,
                        cursor == null ? Long.MAX_VALUE : cursor,
                        size + 1);
        boolean more = rows.size() > size;
        List<HistoryItem> items = more ? rows.subList(0, size) : rows;
        return new History(items, more ? items.getLast().id() : null);
    }

    private User requireUser(Long id) {
        if (id == null) throw new CustomException(GlobalExceptionCode.INVALID_TOKEN);
        return users.findByIdAndStatus(id, Status.ACTIVE)
                .orElseThrow(() -> new CustomException(GlobalExceptionCode.USER_DISABLED));
    }

    public record Summary(
            boolean eligible,
            ActivityGrade activityGrade,
            long totalXp,
            long currentGradeMinXp,
            ActivityGrade nextGrade,
            Long nextGradeMinXp,
            long xpToNextGrade,
            double progressPercent,
            List<ActivityGrade.Threshold> grades) {}

    public record HistoryItem(
            long id,
            String activityType,
            String activityName,
            long deltaXp,
            long totalXp,
            Instant occurredAt,
            Instant processedAt) {}

    public record History(List<HistoryItem> items, Long nextCursor) {}
}
