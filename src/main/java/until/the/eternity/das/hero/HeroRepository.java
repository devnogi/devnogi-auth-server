package until.the.eternity.das.hero;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import until.the.eternity.das.hero.HeroModels.*;

@Repository
@RequiredArgsConstructor
public class HeroRepository {
    private final JdbcTemplate jdbc;

    static LocalDateTime time(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    static Instant instant(ResultSet rs, String name) throws SQLException {
        LocalDateTime value = rs.getObject(name, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    static Slide slide(ResultSet rs, int row) throws SQLException {
        return new Slide(
                rs.getLong("id"),
                rs.getString("image_url"),
                rs.getString("alt_text"),
                rs.getString("link_url"),
                rs.getInt("position"),
                false,
                instant(rs, "starts_at"),
                instant(rs, "ends_at"),
                instant(rs, "deleted_at"));
    }

    static Creative creative(ResultSet rs, int row) throws SQLException {
        return new Creative(
                rs.getLong("id"),
                rs.getString("image_url"),
                rs.getString("alt_text"),
                rs.getString("link_url"),
                rs.getBoolean("blocked"),
                instant(rs, "created_at"));
    }

    static Bid bid(ResultSet rs, int row) throws SQLException {
        return new Bid(
                rs.getLong("id"),
                rs.getLong("auction_id"),
                rs.getLong("creative_id"),
                rs.getLong("amount"),
                rs.getString("status"),
                rs.getString("reason"),
                instant(rs, "created_at"));
    }

    static Auction auction(ResultSet rs, int row) throws SQLException {
        return new Auction(
                rs.getLong("id"),
                rs.getDate("week_start").toLocalDate(),
                rs.getInt("slot"),
                rs.getString("status"),
                instant(rs, "opens_at"),
                instant(rs, "closes_at"),
                instant(rs, "display_starts_at"),
                instant(rs, "display_ends_at"),
                rs.getLong("highest_bid"),
                rs.getObject("highest_bidder_id", Long.class),
                rs.getObject("winning_bid_id", Long.class),
                rs.getString("reason"));
    }

    List<Auction> auctions(String condition, Object... args) {
        return jdbc.query(
                "SELECT a.*,w.opens_at,w.closes_at,w.display_starts_at,w.display_ends_at FROM hero_auction a JOIN hero_week w ON w.week_start=a.week_start "
                        + condition,
                HeroRepository::auction,
                args);
    }

    Auction auction(long id) {
        return auctions("WHERE a.id=?", id).stream()
                .findFirst()
                .orElseThrow(HeroException::missing);
    }

    Bid bid(long id) {
        return jdbc.query("SELECT * FROM hero_bid WHERE id=?", HeroRepository::bid, id).stream()
                .findFirst()
                .orElseThrow(HeroException::missing);
    }

    long insertedId() {
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
