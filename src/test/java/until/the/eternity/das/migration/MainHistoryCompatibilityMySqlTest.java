package until.the.eternity.das.migration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Uses two disposable, empty MySQL instances; never cleans an existing schema. */
class MainHistoryCompatibilityMySqlTest {
    private static final String BASELINE_VERSION = "20250721224045";
    private static final Map<String, String> MAIN_FIXTURE_SHA256 =
            Map.of(
                    "V1__create_init_tables.sql",
                            "ad79f0fd9c18e997492abfd32ab5be748e8f89d9f0586839e4443e0105f2f40e",
                    "V20250616224741__update_users_table.sql",
                            "4c074f8ca28ac6ffe0140d15c9be0cf5bdc5f1a6704cc1261df5b3a37d05b1de",
                    "V20250721220700__drop_table_user_roles.sql",
                            "e35f81e0ee8ac05c6be5e6474ff298f78260313ae84f39712a3c14f93a88adff",
                    "V20250721224045__insert_default_data.sql",
                            "4bc9057eeb3bd86418d36a20308abe0cb2ed640daf1f78e997bb94bcdc8f4d2b",
                    "V20250913200016__user_password_nullable.sql",
                            "a9768a195cce744d63587641790ee8e7a8c66db6df74013bffa5f363ce49e689");

    @Test
    void appliedAndFreshHistoriesMigrateAndMainHistoryRequiresReconciliation() throws Exception {
        String mainUrl = System.getenv("FLYWAY_MAIN_MYSQL_URL");
        String freshUrl = System.getenv("FLYWAY_FRESH_MYSQL_URL");
        String username = System.getenv("FLYWAY_MYSQL_USER");
        String password = System.getenv("FLYWAY_MYSQL_PASSWORD");
        boolean configured =
                present(mainUrl) && present(freshUrl) && present(username) && password != null;
        if (Boolean.parseBoolean(System.getenv("FLYWAY_MYSQL_REQUIRED"))) {
            assertTrue(
                    configured, "Two required MySQL test URLs and credentials must be configured");
        } else {
            assumeTrue(configured, "Two isolated MySQL integration instances are not configured");
        }

        verifyFixtureBytes();
        JdbcTemplate mainDb = jdbc(mainUrl, username, password);
        JdbcTemplate freshDb = jdbc(freshUrl, username, password);
        assertTrue(mainUrl.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/devnogi\\?.*"));
        assertTrue(freshUrl.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/auth\\?.*"));
        assertEquals("devnogi", mainDb.queryForObject("SELECT DATABASE()", String.class));
        assertEquals("auth", freshDb.queryForObject("SELECT DATABASE()", String.class));
        assertNotEquals(
                mainDb.queryForObject("SELECT @@server_uuid", String.class),
                freshDb.queryForObject("SELECT @@server_uuid", String.class),
                "Use separate MySQL instances");
        assertEmptySchema(mainDb);
        assertEmptySchema(freshDb);

        assertEquals(
                0,
                mainDb.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='auth'",
                        Integer.class));
        mainDb.execute("CREATE DATABASE auth CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        String appliedUrl = mainUrl.replaceFirst("/devnogi\\?", "/auth?");
        JdbcTemplate appliedDb = jdbc(appliedUrl, username, password);
        assertEmptySchema(appliedDb);

        // Two independently populated histories coexist on the first disposable server;
        // the genuinely fresh auth history is on the second server.
        flyway(mainUrl, username, password, "classpath:db/main-history", null).migrate();
        flyway(appliedUrl, username, password, "classpath:db/migration", "20261027000001")
                .migrate();
        assertEquals(-1922630474, checksum(mainDb));
        assertEquals(912666893, checksum(appliedDb));

        mainDb.update(
                "INSERT INTO users (email, password_hash, nickname, status, role_id) "
                        + "VALUES ('legacy-upgrade@example.invalid', 'synthetic-hash', 'legacy-name', 'ACTIVE', 3)");
        Long userId =
                mainDb.queryForObject(
                        "SELECT id FROM users WHERE email = 'legacy-upgrade@example.invalid'",
                        Long.class);
        mainDb.update(
                "INSERT INTO refresh_tokens (user_id, token, expires_at, revoked) "
                        + "VALUES (?, 'synthetic-legacy-token', DATE_ADD(NOW(), INTERVAL 1 DAY), TRUE)",
                userId);

        appliedDb.update(
                "INSERT INTO users (email, password_hash, nickname, status, role_id) "
                        + "VALUES ('applied-upgrade@example.invalid', 'synthetic-hash', 'applied-name', 'ACTIVE', 3)");
        appliedDb.update("UPDATE roles SET description='synthetic old description' WHERE id=3");
        appliedDb.update("DELETE FROM roles WHERE id=2");

        Flyway candidateOnMain =
                flyway(mainUrl, username, password, "classpath:db/migration", null);
        FlywayValidateException mismatch =
                assertThrows(FlywayValidateException.class, candidateOnMain::validate);
        assertTrue(mismatch.getMessage().contains("checksum"));
        assertEquals(-1922630474, checksum(mainDb));
        assertEquals(
                0,
                mainDb.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history "
                                + "WHERE version='20261027000002'",
                        Integer.class));
        var unchangedHistory =
                mainDb.queryForList(
                        "SELECT installed_rank, version, description, type, script, checksum, success "
                                + "FROM flyway_schema_history WHERE version <> ? ORDER BY installed_rank",
                        BASELINE_VERSION);

        // Explicitly simulate Flyway repair on the disposable main-history database only.
        // The row/data assertions above and below ensure the synthetic reconciliation is visible.
        candidateOnMain.repair();
        assertEquals(912666893, checksum(mainDb));
        assertEquals(
                unchangedHistory,
                mainDb.queryForList(
                        "SELECT installed_rank, version, description, type, script, checksum, success "
                                + "FROM flyway_schema_history WHERE version <> ? ORDER BY installed_rank",
                        BASELINE_VERSION));
        candidateOnMain.migrate();
        candidateOnMain.validate();
        Flyway candidateOnApplied =
                flyway(appliedUrl, username, password, "classpath:db/migration", null);
        candidateOnApplied.migrate();
        candidateOnApplied.validate();
        flyway(freshUrl, username, password, "classpath:db/migration", null).migrate();

        assertEquals(912666893, checksum(appliedDb));
        assertEquals(912666893, checksum(freshDb));
        assertEquals(
                "legacy-name:USER",
                mainDb.queryForObject(
                        "SELECT CONCAT(u.nickname, ':', r.name) "
                                + "FROM users u JOIN roles r ON r.id = u.role_id WHERE u.id = ?",
                        String.class,
                        userId));
        assertEquals(
                "applied-name:USER",
                appliedDb.queryForObject(
                        "SELECT CONCAT(u.nickname, ':', r.name) "
                                + "FROM users u JOIN roles r ON r.id=u.role_id WHERE u.email='applied-upgrade@example.invalid'",
                        String.class));
        assertEquals(
                1,
                mainDb.queryForObject(
                        "SELECT COUNT(*) FROM refresh_tokens "
                                + "WHERE user_id = ? AND token = 'synthetic-legacy-token' AND revoked = TRUE",
                        Integer.class,
                        userId));
        assertEquals(roles(mainDb), roles(freshDb));
        assertEquals(roles(appliedDb), roles(freshDb));
        assertEquals(List.of("1:SUPER_ADMIN", "2:ADMIN", "3:USER"), roleKeys(freshDb));
        assertEquals(
                schemaSnapshot(mainDb),
                schemaSnapshot(freshDb),
                "Upgraded and fresh databases must have the same final schema");
        assertEquals(schemaSnapshot(appliedDb), schemaSnapshot(freshDb));
        assertEquals(
                8192,
                freshDb.queryForObject(
                        "SELECT character_maximum_length FROM information_schema.columns "
                                + "WHERE table_schema = DATABASE() AND table_name = 'refresh_tokens' AND column_name = 'token'",
                        Integer.class));
        assertEquals(
                0,
                freshDb.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.schemata "
                                + "WHERE schema_name = 'devnogi'",
                        Integer.class),
                "Fresh auth must not require a devnogi schema");

        assertArrayEquals(
                resource("/db/migration/V20261027000002__ensure_default_roles.sql"),
                resource("/db/role-replay/V20261027000003__ensure_default_roles_replay.sql"));
        Flyway.configure()
                .dataSource(appliedUrl, username, password)
                .locations("classpath:db/migration", "classpath:db/role-replay")
                .load()
                .migrate();
        assertEquals(roles(appliedDb), roles(freshDb), "Replaying the SQL must be idempotent");
        assertEquals(
                "applied-name:USER",
                appliedDb.queryForObject(
                        "SELECT CONCAT(u.nickname, ':', r.name) "
                                + "FROM users u JOIN roles r ON r.id=u.role_id WHERE u.email='applied-upgrade@example.invalid'",
                        String.class));
        assertRoleConflict(
                mainDb,
                mainUrl,
                username,
                password,
                "v7_role_conflict_id",
                "INSERT INTO roles VALUES (1,'OTHER','unchanged')");
        assertRoleConflict(
                mainDb,
                mainUrl,
                username,
                password,
                "v7_role_conflict_name",
                "INSERT INTO roles VALUES (99,'user','unchanged')");
    }

    private static Integer checksum(JdbcTemplate db) {
        return db.queryForObject(
                "SELECT checksum FROM flyway_schema_history WHERE version=? AND success=1",
                Integer.class,
                BASELINE_VERSION);
    }

    private static void assertRoleConflict(
            JdbcTemplate server,
            String mainUrl,
            String user,
            String password,
            String schema,
            String insert) {
        assertEquals(
                0,
                server.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name=?",
                        Integer.class,
                        schema));
        server.execute(
                "CREATE DATABASE `"
                        + schema
                        + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        String url = mainUrl.replaceFirst("/devnogi\\?", "/" + schema + "?");
        JdbcTemplate db = jdbc(url, user, password);
        db.execute(
                "CREATE TABLE roles (id BIGINT PRIMARY KEY, name VARCHAR(50) NOT NULL UNIQUE, description VARCHAR(255))");
        db.execute(insert);
        var before = roles(db);
        Flyway candidate =
                Flyway.configure()
                        .dataSource(url, user, password)
                        .locations("classpath:db/migration")
                        .baselineOnMigrate(true)
                        .baselineVersion("20261027000001")
                        .load();
        FlywayException failure = assertThrows(FlywayException.class, candidate::migrate);
        assertTrue(
                hasRoleConflictSignal(failure),
                "Migration must fail with the explicit default-role conflict SIGNAL");
        assertEquals(before, roles(db));
    }

    private static boolean hasRoleConflictSignal(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && "45000".equals(sqlException.getSQLState())
                    && sqlException.getMessage() != null
                    && sqlException
                            .getMessage()
                            .contains(
                                    "Default role identifier or name conflicts with existing data")) {
                return true;
            }
        }
        return false;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static JdbcTemplate jdbc(String url, String username, String password) {
        return new JdbcTemplate(new DriverManagerDataSource(url, username, password));
    }

    private static Flyway flyway(
            String url, String username, String password, String location, String target) {
        var config = Flyway.configure().dataSource(url, username, password).locations(location);
        if (target != null) {
            config.target(MigrationVersion.fromVersion(target));
        }
        return config.load();
    }

    private static void assertEmptySchema(JdbcTemplate db) {
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE()",
                        Integer.class),
                "Use a new empty disposable database");
    }

    private static List<List<Map<String, Object>>> schemaSnapshot(JdbcTemplate db) {
        return List.of(
                db.queryForList(
                        "SELECT table_name, table_comment, table_collation FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history' ORDER BY table_name"),
                db.queryForList(
                        "SELECT table_name, column_name, ordinal_position, column_type, is_nullable, column_default, "
                                + "extra, column_comment FROM information_schema.columns WHERE table_schema = DATABASE() "
                                + "AND table_name <> 'flyway_schema_history' ORDER BY table_name, ordinal_position"),
                db.queryForList(
                        "SELECT table_name, index_name, non_unique, seq_in_index, column_name, sub_part, "
                                + "collation, index_type FROM information_schema.statistics WHERE table_schema = DATABASE() "
                                + "AND table_name <> 'flyway_schema_history' ORDER BY table_name, index_name, seq_in_index"),
                db.queryForList(
                        "SELECT table_name, constraint_name, column_name, ordinal_position, "
                                + "referenced_table_name, referenced_column_name FROM information_schema.key_column_usage "
                                + "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history' "
                                + "ORDER BY table_name, constraint_name, ordinal_position"),
                db.queryForList(
                        "SELECT table_name, constraint_name, update_rule, delete_rule "
                                + "FROM information_schema.referential_constraints WHERE constraint_schema = DATABASE() "
                                + "ORDER BY table_name, constraint_name"));
    }

    private static List<Map<String, Object>> roles(JdbcTemplate db) {
        return db.queryForList("SELECT id, name, description FROM roles ORDER BY id");
    }

    private static List<String> roleKeys(JdbcTemplate db) {
        return db.queryForList("SELECT CONCAT(id, ':', name) FROM roles ORDER BY id", String.class);
    }

    private static void verifyFixtureBytes() throws Exception {
        for (var fixture : MAIN_FIXTURE_SHA256.entrySet()) {
            byte[] fixtureBytes = resource("/db/main-history/" + fixture.getKey());
            assertEquals(
                    fixture.getValue(),
                    normalizedSha256(fixtureBytes),
                    "main migration fixture changed: " + fixture.getKey());
            if (!fixture.getKey().equals("V20250721224045__insert_default_data.sql")) {
                assertArrayEquals(
                        fixtureBytes,
                        resource("/db/migration/" + fixture.getKey()),
                        "candidate historical migration bytes differ from the fixed main fixture: "
                                + fixture.getKey());
            }
        }
        assertEquals(
                "a5e444d205bc36db28255a9141483e154f659e408c7a317f90c9599f842e7614",
                normalizedSha256(
                        resource("/db/altered-dev/V20250721224045__insert_default_data.sql")));
        assertArrayEquals(
                resource("/db/altered-dev/V20250721224045__insert_default_data.sql"),
                resource("/db/migration/V20250721224045__insert_default_data.sql"));
        assertEquals(
                -1922630474,
                flywayChecksum(
                        resource("/db/main-history/V20250721224045__insert_default_data.sql")));
        assertEquals(
                912666893,
                flywayChecksum(resource("/db/migration/V20250721224045__insert_default_data.sql")));
    }

    private static byte[] resource(String path) throws Exception {
        try (InputStream input =
                MainHistoryCompatibilityMySqlTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            return input.readAllBytes();
        }
    }

    private static String normalizedSha256(byte[] bytes) throws Exception {
        String normalized = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n");
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(normalized.getBytes(StandardCharsets.UTF_8)));
    }

    private static int flywayChecksum(byte[] bytes) {
        CRC32 checksum = new CRC32();
        new String(bytes, StandardCharsets.UTF_8)
                .lines()
                .forEach(line -> checksum.update(line.getBytes(StandardCharsets.UTF_8)));
        return (int) checksum.getValue();
    }
}
