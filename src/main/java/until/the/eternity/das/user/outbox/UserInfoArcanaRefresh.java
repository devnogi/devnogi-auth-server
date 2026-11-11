package until.the.eternity.das.user.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Explicit post-drain replay. The marker is never advanced by ordinary snapshot writers. */
@RequiredArgsConstructor
public class UserInfoArcanaRefresh {
    private final JdbcTemplate jdbc;

    @Transactional
    public int refresh(int batchSize) {
        if (batchSize < 1 || batchSize > 1000)
            throw new IllegalArgumentException("Batch size must be 1..1000");
        var users =
                jdbc.query(
                        "SELECT u.id FROM users u LEFT JOIN user_info_outbox o ON o.user_id=u.id "
                                + "WHERE o.user_id IS NULL OR o.arcana_refreshed=FALSE "
                                + "ORDER BY u.id LIMIT ? FOR UPDATE SKIP LOCKED",
                        (rs, row) -> rs.getLong(1),
                        batchSize);
        for (Long id : users) {
            jdbc.update(
                    """
                    INSERT INTO user_info_outbox
                    (user_id,revision,published_revision,nickname,profile_image_url,server_name,
                     verified,activity_grade,arcana,arcana_refreshed,attempt_count,next_attempt_at,pending_since,created_at,updated_at)
                    SELECT u.id,1,0,u.nickname,u.profile_image_url,u.server_name,u.is_verified,
                      CASE WHEN u.is_verified=TRUE AND u.status='ACTIVE' THEN u.grade ELSE NULL END,
                      p.arcana,TRUE,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
                    FROM users u LEFT JOIN user_game_profile p ON p.user_id=u.id WHERE u.id=?
                    ON DUPLICATE KEY UPDATE
                      pending_since=IF(published_revision>=revision,VALUES(pending_since),pending_since),
                      revision=revision+1,nickname=VALUES(nickname),profile_image_url=VALUES(profile_image_url),
                      server_name=VALUES(server_name),verified=VALUES(verified),activity_grade=VALUES(activity_grade),
                      arcana=VALUES(arcana),arcana_refreshed=TRUE,attempt_count=0,
                      next_attempt_at=VALUES(next_attempt_at),updated_at=VALUES(updated_at)
                    """,
                    id);
        }
        return users.size();
    }

    public long remaining() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM users u LEFT JOIN user_info_outbox o ON o.user_id=u.id "
                        + "WHERE o.user_id IS NULL OR o.arcana_refreshed=FALSE",
                Long.class);
    }
}
