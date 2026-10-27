package until.the.eternity.das.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Uses two disposable, empty MySQL instances; never cleans an existing schema. */
class MainHistoryCompatibilityMySqlTest {
  private static final String BASELINE_VERSION = "20250721224045";
  private static final Map<String, String> MAIN_FIXTURE_SHA256 = Map.of(
    "V1__create_init_tables.sql", "ad79f0fd9c18e997492abfd32ab5be748e8f89d9f0586839e4443e0105f2f40e",
    "V20250616224741__update_users_table.sql", "4c074f8ca28ac6ffe0140d15c9be0cf5bdc5f1a6704cc1261df5b3a37d05b1de",
    "V20250721220700__drop_table_user_roles.sql", "e35f81e0ee8ac05c6be5e6474ff298f78260313ae84f39712a3c14f93a88adff",
    "V20250721224045__insert_default_data.sql", "4bc9057eeb3bd86418d36a20308abe0cb2ed640daf1f78e997bb94bcdc8f4d2b",
    "V20250913200016__user_password_nullable.sql", "a9768a195cce744d63587641790ee8e7a8c66db6df74013bffa5f363ce49e689"
  );

  @Test
  void mainHistoryUpgradesAndFreshBaselineMatchesHistoricalSchema() throws Exception {
    String mainUrl = System.getenv("FLYWAY_MAIN_MYSQL_URL");
    String freshUrl = System.getenv("FLYWAY_FRESH_MYSQL_URL");
    String username = System.getenv("FLYWAY_MYSQL_USER");
    String password = System.getenv("FLYWAY_MYSQL_PASSWORD");
    boolean configured = present(mainUrl) && present(freshUrl) && present(username) && password != null;
    if (Boolean.parseBoolean(System.getenv("FLYWAY_MYSQL_REQUIRED"))) {
      assertTrue(configured, "Two required MySQL test URLs and credentials must be configured");
    } else {
      assumeTrue(configured, "Two isolated MySQL integration instances are not configured");
    }

    verifyFixtureBytes();
    JdbcTemplate mainDb = jdbc(mainUrl, username, password);
    JdbcTemplate freshDb = jdbc(freshUrl, username, password);
    assertEquals("devnogi", mainDb.queryForObject("SELECT DATABASE()", String.class));
    assertEquals("auth", freshDb.queryForObject("SELECT DATABASE()", String.class));
    assertNotEquals(mainDb.queryForObject("SELECT @@server_uuid", String.class),
      freshDb.queryForObject("SELECT @@server_uuid", String.class), "Use separate MySQL instances");
    assertEmptySchema(mainDb);
    assertEmptySchema(freshDb);

    Flyway mainAtBaseline = flyway(mainUrl, username, password, "classpath:db/main-history", BASELINE_VERSION);
    Flyway freshAtBaseline = flyway(freshUrl, username, password, "classpath:db/migration", BASELINE_VERSION);
    mainAtBaseline.migrate();
    freshAtBaseline.migrate();

    assertEquals(1, freshDb.queryForObject("SELECT COUNT(*) FROM flyway_schema_history "
      + "WHERE script = 'B20250721224045__auth_schema_baseline.sql' AND success = 1", Integer.class));
    assertEquals(schemaSnapshot(mainDb), schemaSnapshot(freshDb),
      "The baseline must reproduce the schema immediately after the historical V migrations");
    assertEquals(roles(mainDb), roles(freshDb));
    assertEquals(List.of("1:SUPER_ADMIN", "2:ADMIN", "3:USER"), roleKeys(mainDb));

    flyway(mainUrl, username, password, "classpath:db/main-history", null).migrate();
    Integer originalChecksum = mainDb.queryForObject("SELECT checksum FROM flyway_schema_history "
      + "WHERE version = ? AND success = 1", Integer.class, BASELINE_VERSION);
    assertNotNull(originalChecksum);
    mainDb.update("INSERT INTO users (email, password_hash, nickname, status, role_id) "
      + "VALUES ('legacy-upgrade@example.invalid', 'synthetic-hash', 'legacy-name', 'ACTIVE', 3)");
    Long userId = mainDb.queryForObject("SELECT id FROM users WHERE email = 'legacy-upgrade@example.invalid'", Long.class);
    mainDb.update("INSERT INTO refresh_tokens (user_id, token, expires_at, revoked) "
      + "VALUES (?, 'synthetic-legacy-token', DATE_ADD(NOW(), INTERVAL 1 DAY), TRUE)", userId);

    Flyway candidateOnMain = flyway(mainUrl, username, password, "classpath:db/migration", null);
    candidateOnMain.migrate();
    candidateOnMain.validate();
    flyway(freshUrl, username, password, "classpath:db/migration", null).migrate();

    assertEquals(originalChecksum, mainDb.queryForObject("SELECT checksum FROM flyway_schema_history "
      + "WHERE version = ? AND success = 1", Integer.class, BASELINE_VERSION));
    assertEquals(0, mainDb.queryForObject("SELECT COUNT(*) FROM flyway_schema_history "
      + "WHERE script LIKE 'B%'", Integer.class), "Baseline must not execute on an existing history");
    assertEquals("legacy-name:USER", mainDb.queryForObject("SELECT CONCAT(u.nickname, ':', r.name) "
      + "FROM users u JOIN roles r ON r.id = u.role_id WHERE u.id = ?", String.class, userId));
    assertEquals(1, mainDb.queryForObject("SELECT COUNT(*) FROM refresh_tokens "
      + "WHERE user_id = ? AND token = 'synthetic-legacy-token' AND revoked = TRUE", Integer.class, userId));
    assertEquals(roles(mainDb), roles(freshDb));
    assertEquals(schemaSnapshot(mainDb), schemaSnapshot(freshDb),
      "Upgraded and fresh databases must have the same final schema");
    assertEquals(8192, freshDb.queryForObject("SELECT character_maximum_length FROM information_schema.columns "
      + "WHERE table_schema = DATABASE() AND table_name = 'refresh_tokens' AND column_name = 'token'", Integer.class));
    assertEquals(0, freshDb.queryForObject("SELECT COUNT(*) FROM information_schema.schemata "
      + "WHERE schema_name = 'devnogi'", Integer.class), "Fresh auth must not require a devnogi schema");

    // Simulate a separately deployed dev history on this disposable database only.
    // This is not Flyway repair: validation must fail and leave the recorded checksum untouched.
    byte[] mainSeed = resource("/db/main-history/V20250721224045__insert_default_data.sql");
    byte[] changedDevSeed = resource("/db/altered-dev/V20250721224045__insert_default_data.sql");
    assertEquals(originalChecksum, flywayChecksum(mainSeed));
    int changedDevChecksum = flywayChecksum(changedDevSeed);
    assertNotEquals(originalChecksum, changedDevChecksum);
    mainDb.update("UPDATE flyway_schema_history SET checksum = ? WHERE version = ?",
      changedDevChecksum, BASELINE_VERSION);
    FlywayValidateException mismatch = assertThrows(FlywayValidateException.class, candidateOnMain::validate);
    assertTrue(mismatch.getMessage().contains("checksum"));
    assertEquals(changedDevChecksum, mainDb.queryForObject("SELECT checksum FROM flyway_schema_history "
      + "WHERE version = ? AND success = 1", Integer.class, BASELINE_VERSION));
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  private static JdbcTemplate jdbc(String url, String username, String password) {
    return new JdbcTemplate(new DriverManagerDataSource(url, username, password));
  }

  private static Flyway flyway(String url, String username, String password, String location, String target) {
    var config = Flyway.configure().dataSource(url, username, password).locations(location);
    if (target != null) {
      config.target(MigrationVersion.fromVersion(target));
    }
    return config.load();
  }

  private static void assertEmptySchema(JdbcTemplate db) {
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
      + "WHERE table_schema = DATABASE()", Integer.class), "Use a new empty disposable database");
  }

  private static List<List<Map<String, Object>>> schemaSnapshot(JdbcTemplate db) {
    return List.of(
      db.queryForList("SELECT table_name, table_comment, table_collation FROM information_schema.tables "
        + "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history' ORDER BY table_name"),
      db.queryForList("SELECT table_name, column_name, ordinal_position, column_type, is_nullable, column_default, "
        + "extra, column_comment FROM information_schema.columns WHERE table_schema = DATABASE() "
        + "AND table_name <> 'flyway_schema_history' ORDER BY table_name, ordinal_position"),
      db.queryForList("SELECT table_name, index_name, non_unique, seq_in_index, column_name, sub_part, "
        + "collation, index_type FROM information_schema.statistics WHERE table_schema = DATABASE() "
        + "AND table_name <> 'flyway_schema_history' ORDER BY table_name, index_name, seq_in_index"),
      db.queryForList("SELECT table_name, constraint_name, column_name, ordinal_position, "
        + "referenced_table_name, referenced_column_name FROM information_schema.key_column_usage "
        + "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history' "
        + "ORDER BY table_name, constraint_name, ordinal_position"),
      db.queryForList("SELECT table_name, constraint_name, update_rule, delete_rule "
        + "FROM information_schema.referential_constraints WHERE constraint_schema = DATABASE() "
        + "ORDER BY table_name, constraint_name")
    );
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
      assertEquals(fixture.getValue(), normalizedSha256(fixtureBytes),
        "main migration fixture changed: " + fixture.getKey());
      assertArrayEquals(fixtureBytes, resource("/db/migration/" + fixture.getKey()),
        "candidate historical migration bytes differ from the fixed main fixture: " + fixture.getKey());
    }
    assertEquals("a5e444d205bc36db28255a9141483e154f659e408c7a317f90c9599f842e7614",
      normalizedSha256(resource("/db/altered-dev/V20250721224045__insert_default_data.sql")));
  }

  private static byte[] resource(String path) throws Exception {
    try (InputStream input = MainHistoryCompatibilityMySqlTest.class.getResourceAsStream(path)) {
      assertNotNull(input, path);
      return input.readAllBytes();
    }
  }

  private static String normalizedSha256(byte[] bytes) throws Exception {
    String normalized = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n");
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
      .digest(normalized.getBytes(StandardCharsets.UTF_8)));
  }

  private static int flywayChecksum(byte[] bytes) {
    CRC32 checksum = new CRC32();
    new String(bytes, StandardCharsets.UTF_8).lines()
      .forEach(line -> checksum.update(line.getBytes(StandardCharsets.UTF_8)));
    return (int) checksum.getValue();
  }
}
