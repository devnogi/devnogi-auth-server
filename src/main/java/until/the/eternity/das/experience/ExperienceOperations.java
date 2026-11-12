package until.the.eternity.das.experience;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExperienceOperations {
    private final JdbcTemplate jdbc;

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public LocalDateTime freeze() {
        // Serialize operators, then lock the same users that verification and consumption lock.
        LocalDateTime existing =
                jdbc.queryForObject(
                        "SELECT rollout_at FROM experience_activity "
                                + "WHERE code = 'POST_PUBLISHED' AND policy_version = 1 FOR UPDATE",
                        (rs, row) -> rs.getObject(1, LocalDateTime.class));
        if (existing != null) return existing;
        jdbc.query("SELECT id FROM users ORDER BY id FOR UPDATE", (rs, row) -> rs.getLong(1));
        LocalDateTime cutoff =
                jdbc.queryForObject(
                        "SELECT UTC_TIMESTAMP(6)",
                        (rs, row) -> rs.getObject(1, LocalDateTime.class));
        // Re-establish the boundary after old Auth instances have drained during rollout.
        jdbc.update(
                "UPDATE user_verification_history SET valid_until = ? WHERE experience_valid_from IS NOT NULL AND valid_until IS NULL",
                cutoff);
        jdbc.update(
                """
                INSERT INTO user_verification_history
                (user_id, server_name, character_name, verified_at, verification_success,
                 created_at, experience_valid_from, experience_baseline)
                SELECT id, server_name, nickname, ?, TRUE, ?, ?, TRUE FROM users
                WHERE is_verified = TRUE AND status = 'ACTIVE'
                """,
                cutoff,
                cutoff,
                cutoff);
        jdbc.update(
                """
                INSERT INTO user_experience(user_id, total_xp, backfill_eligible, updated_at)
                SELECT id, 0, (is_verified = TRUE AND status = 'ACTIVE'), ? FROM users
                ON DUPLICATE KEY UPDATE backfill_eligible = VALUES(backfill_eligible)
                """,
                cutoff);
        jdbc.update(
                "UPDATE experience_activity SET rollout_at = ? WHERE policy_version = 1", cutoff);
        jdbc.update(
                """
                UPDATE users u JOIN user_experience e ON e.user_id = u.id
                LEFT JOIN (SELECT user_id, SUM(amount) AS spent FROM hero_charge GROUP BY user_id) c ON c.user_id = u.id
                SET u.grade = CASE WHEN e.total_xp - COALESCE(c.spent, 0) >= 5000 THEN 'GRANDMASTER'
                  WHEN e.total_xp - COALESCE(c.spent, 0) >= 2500 THEN 'MASTER'
                  WHEN e.total_xp - COALESCE(c.spent, 0) >= 1000 THEN 'CHAMPION'
                  WHEN e.total_xp - COALESCE(c.spent, 0) >= 250 THEN 'EXPERT'
                  WHEN e.total_xp - COALESCE(c.spent, 0) >= 45 THEN 'SENIOR'
                  ELSE 'APPRENTICE' END
                WHERE u.is_verified = TRUE AND u.status = 'ACTIVE'
                """);
        // A pre-upgrade publisher may have acknowledged a snapshot without the new field.
        jdbc.update(
                """
                INSERT INTO user_info_outbox
                (user_id, revision, published_revision, nickname, profile_image_url, server_name,
                 verified, activity_grade, arcana, attempt_count, next_attempt_at, pending_since, created_at, updated_at)
                SELECT u.id, 1, 0, u.nickname, u.profile_image_url, u.server_name, u.is_verified, u.grade,
                       p.arcana, 0, ?, ?, ?, ? FROM users u LEFT JOIN user_game_profile p ON p.user_id=u.id
                       WHERE u.is_verified = TRUE AND u.status = 'ACTIVE'
                ON DUPLICATE KEY UPDATE
                  pending_since = IF(published_revision >= revision, VALUES(pending_since), pending_since),
                  revision = revision + 1, nickname = VALUES(nickname),
                  profile_image_url = VALUES(profile_image_url), server_name = VALUES(server_name),
                  verified = VALUES(verified), activity_grade = VALUES(activity_grade),
                  arcana = VALUES(arcana),
                  attempt_count = 0, next_attempt_at = VALUES(next_attempt_at), updated_at = VALUES(updated_at)
                """,
                cutoff,
                cutoff,
                cutoff,
                cutoff);
        return cutoff;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dryRun() {
        return jdbc.queryForObject(
                """
                SELECT COUNT(*) AS users, COALESCE(SUM(is_verified = TRUE AND status = 'ACTIVE'), 0) AS eligible_users,
                (SELECT MIN(rollout_at) FROM experience_activity WHERE policy_version = 1) AS frozen_at
                FROM users
                """,
                (rs, row) -> {
                    Map<String, Object> result = new java.util.LinkedHashMap<>();
                    result.put("users", rs.getLong("users"));
                    result.put("eligible_users", rs.getLong("eligible_users"));
                    result.put("frozen_at", rs.getObject("frozen_at", LocalDateTime.class));
                    return result;
                });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> reconcile() {
        Map<String, Object> result =
                new java.util.LinkedHashMap<>(
                        jdbc.queryForMap(
                                """
                SELECT COUNT(*) AS users_checked,
                  COALESCE(SUM(COALESCE(e.total_xp, 0) <> COALESCE(h.total, 0)), 0) AS ledger_mismatches,
                  COALESCE(SUM((u.grade IS NULL AND (COALESCE(e.total_xp, 0) > 0 OR u.is_verified = TRUE))
                    OR (u.grade IS NOT NULL AND u.grade <> CASE
                    WHEN COALESCE(e.total_xp, 0) - COALESCE(c.spent, 0) >= 5000 THEN 'GRANDMASTER'
                    WHEN COALESCE(e.total_xp, 0) - COALESCE(c.spent, 0) >= 2500 THEN 'MASTER'
                    WHEN COALESCE(e.total_xp, 0) - COALESCE(c.spent, 0) >= 1000 THEN 'CHAMPION'
                    WHEN COALESCE(e.total_xp, 0) - COALESCE(c.spent, 0) >= 250 THEN 'EXPERT'
                    WHEN COALESCE(e.total_xp, 0) - COALESCE(c.spent, 0) >= 45 THEN 'SENIOR' ELSE 'APPRENTICE' END)), 0) AS grade_mismatches
                FROM users u LEFT JOIN user_experience e ON e.user_id = u.id
                LEFT JOIN (SELECT user_id, SUM(amount) AS spent FROM hero_charge GROUP BY user_id) c ON c.user_id = u.id
                LEFT JOIN (SELECT user_id, SUM(delta_xp) AS total FROM user_experience_history GROUP BY user_id) h
                  ON h.user_id = u.id
                """));
        result.put(
                "negative_advertising_balances",
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM (SELECT user_id FROM hero_charge GROUP BY user_id HAVING SUM(amount) < 0) invalid",
                        Long.class));
        result.put(
                "reservation_mismatches",
                jdbc.queryForObject(
                        """
                SELECT COUNT(*) FROM hero_auction a
                LEFT JOIN hero_bid b ON b.id = a.highest_bid_id
                LEFT JOIN hero_reservation r ON r.auction_id = a.id
                WHERE (a.status = 'OPEN' AND b.status = 'LEADING'
                  AND (r.auction_id IS NULL OR r.user_id <> b.user_id OR r.amount <> b.amount))
                  OR (r.auction_id IS NOT NULL AND
                    (a.status <> 'OPEN' OR b.id IS NULL OR b.status <> 'LEADING'))
                """,
                        Long.class));
        result.put(
                "payment_mismatches",
                jdbc.queryForObject(
                        """
                SELECT COUNT(*) FROM hero_charge c
                LEFT JOIN hero_auction a ON a.id = c.auction_id
                LEFT JOIN hero_bid b ON b.id = a.winning_bid_id
                LEFT JOIN hero_charge original ON original.auction_id = c.auction_id AND original.kind = 'CHARGE'
                WHERE (c.kind = 'CHARGE' AND (b.id IS NULL OR c.amount <> b.amount OR c.user_id <> b.user_id))
                  OR (c.kind = 'REFUND' AND (original.id IS NULL OR c.amount <> -original.amount
                    OR c.user_id <> original.user_id))
                """,
                        Long.class));
        return result;
    }
}
