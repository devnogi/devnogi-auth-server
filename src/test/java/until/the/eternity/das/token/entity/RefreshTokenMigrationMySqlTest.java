package until.the.eternity.das.token.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "TOKEN_MIGRATION_MYSQL_REQUIRED", matches = "true")
class RefreshTokenMigrationMySqlTest {
    @Test
    void upgradePreservesOldTokenAndForeignKeyThenAcceptsLongToken() {
        String url = System.getenv("TOKEN_MIGRATION_MYSQL_URL");
        String user = System.getenv("TOKEN_MIGRATION_MYSQL_USER");
        String password = System.getenv("TOKEN_MIGRATION_MYSQL_PASSWORD");
        assertTrue(url != null && !url.isBlank() && user != null && password != null);

        Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .target("20261027000000")
                .load()
                .migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        assertColumn(jdbc, 512);

        String email = "upgrade-" + UUID.randomUUID() + "@example.invalid";
        jdbc.update(
                "insert into users(email, password_hash, nickname, status, role_id, is_verified) "
                        + "values (?, 'synthetic-hash', ?, 'ACTIVE', 3, false)",
                email,
                "합성 이전 계정");
        Long userId = jdbc.queryForObject("select id from users where email=?", Long.class, email);
        String oldToken = "legacy." + "x".repeat(456);
        jdbc.update(
                "insert into refresh_tokens(user_id, token, expires_at, created_at, revoked) "
                        + "values (?, ?, date_add(now(), interval 1 day), now(), true)",
                userId,
                oldToken);
        Long tokenId =
                jdbc.queryForObject(
                        "select id from refresh_tokens where token=?", Long.class, oldToken);

        Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        assertColumn(jdbc, 8192);
        assertEquals(
                oldToken,
                jdbc.queryForObject(
                        "select token from refresh_tokens where id=? and user_id=? and revoked=true",
                        String.class,
                        tokenId,
                        userId));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from information_schema.statistics "
                                + "where table_schema=database() and table_name='refresh_tokens' "
                                + "and index_name='idx_refresh_token_user_id'",
                        Integer.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from information_schema.key_column_usage "
                                + "where table_schema=database() and table_name='refresh_tokens' "
                                + "and column_name='user_id' and referenced_table_name='users'",
                        Integer.class));

        String longToken = "new." + "y".repeat(4111);
        jdbc.update(
                "insert into refresh_tokens(user_id, token, expires_at, created_at, revoked) "
                        + "values (?, ?, date_add(now(), interval 1 day), now(), false)",
                userId,
                longToken);
        assertEquals(
                longToken,
                jdbc.queryForObject(
                        "select token from refresh_tokens where token=?", String.class, longToken));
    }

    private void assertColumn(JdbcTemplate jdbc, int expectedLength) {
        assertEquals(
                expectedLength,
                jdbc.queryForObject(
                        "select character_maximum_length "
                                + "from information_schema.columns where table_schema=database() "
                                + "and table_name='refresh_tokens' and column_name='token'",
                        Integer.class));
        assertEquals(
                "NO",
                jdbc.queryForObject(
                        "select is_nullable from information_schema.columns "
                                + "where table_schema=database() and table_name='refresh_tokens' and column_name='token'",
                        String.class));
    }
}
