package until.the.eternity.das.hero;

import static until.the.eternity.das.hero.HeroRepository.instant;
import static until.the.eternity.das.hero.HeroRepository.time;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import until.the.eternity.das.hero.HeroModels.AdminCreativePage;
import until.the.eternity.das.hero.HeroModels.AdminHeroCreative;

/** Administrator-only projection; user/public creative responses never contain owner details. */
@Service
@RequiredArgsConstructor
public class HeroAdminCreativeSearch {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminCreativePage search(int page, int size, String query, String status) {
        if (page < 0 || size < 1 || size > 50)
            throw HeroException.invalid("페이지는 0 이상, 조회 개수는 1~50개여야 합니다.");
        if (query == null || query.length() > 100)
            throw HeroException.invalid("검색어는 100자 이하여야 합니다.");
        if (!List.of("ALL", "AVAILABLE", "BLOCKED").contains(status))
            throw HeroException.invalid("광고 소재 상태가 올바르지 않습니다.");

        StringBuilder where = new StringBuilder(" WHERE c.deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        if (!"ALL".equals(status)) {
            where.append(" AND c.blocked=?");
            args.add("BLOCKED".equals(status));
        }
        String term = query.strip();
        if (!term.isEmpty()) {
            where.append(
                    " AND (c.alt_text LIKE ? ESCAPE '!' OR c.link_url LIKE ? ESCAPE '!'"
                            + " OR u.nickname LIKE ? ESCAPE '!' OR CAST(c.id AS CHAR)=? OR CAST(c.user_id AS CHAR)=?)");
            String pattern =
                    "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            args.addAll(List.of(pattern, pattern, pattern, term, term));
        }
        String from = " FROM hero_creative c JOIN users u ON u.id=c.user_id";
        long total =
                jdbc.queryForObject("SELECT COUNT(*)" + from + where, Long.class, args.toArray());
        var now = time(clock.instant());
        List<Object> itemArgs = new ArrayList<>(List.of(now, now, now));
        itemArgs.addAll(args);
        itemArgs.add(size);
        itemArgs.add((long) page * size);
        List<AdminHeroCreative> items =
                jdbc.query(
                        """
                        SELECT c.*,u.nickname,
                          CASE WHEN c.blocked THEN
                            (SELECT a.detail FROM hero_audit a WHERE a.action='CREATIVE_BLOCK'
                             AND a.target_id=c.id ORDER BY a.id DESC LIMIT 1)
                          ELSE NULL END AS block_reason,
                          (SELECT COUNT(*) FROM hero_bid b WHERE b.creative_id=c.id
                           AND b.status='LEADING' AND c.blocked=FALSE) AS leading_bid_count,
                          (SELECT COUNT(*) FROM hero_placement p JOIN hero_bid b ON b.id=p.bid_id
                           WHERE b.creative_id=c.id AND b.status='WON' AND c.blocked=FALSE
                           AND p.stopped_at IS NULL AND p.starts_at>?) AS scheduled_placement_count,
                          (SELECT COUNT(*) FROM hero_placement p JOIN hero_bid b ON b.id=p.bid_id
                           WHERE b.creative_id=c.id AND b.status='WON' AND c.blocked=FALSE
                           AND p.stopped_at IS NULL AND p.starts_at<=? AND p.ends_at>?) AS active_placement_count
                        """
                                + from
                                + where
                                + " ORDER BY c.id DESC LIMIT ? OFFSET ?",
                        (rs, row) ->
                                new AdminHeroCreative(
                                        rs.getLong("id"),
                                        rs.getString("image_url"),
                                        rs.getString("alt_text"),
                                        rs.getString("link_url"),
                                        rs.getBoolean("blocked"),
                                        instant(rs, "created_at"),
                                        rs.getLong("user_id"),
                                        rs.getString("nickname"),
                                        rs.getString("block_reason"),
                                        rs.getLong("leading_bid_count"),
                                        rs.getLong("scheduled_placement_count"),
                                        rs.getLong("active_placement_count")),
                        itemArgs.toArray());
        return new AdminCreativePage(items, page, size, total, (total + size - 1) / size);
    }
}
