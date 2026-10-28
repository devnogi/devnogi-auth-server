package until.the.eternity.das.user.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class NicknameMigrationMySqlTest {

    @Test
    void actualFlywayHistoryPreservesLegacyNameAndRejectsDuplicateUnverifiedName()
            throws Exception {
        String url = System.getenv("NICKNAME_MYSQL_URL");
        String username = System.getenv("NICKNAME_MYSQL_USER");
        String password = System.getenv("NICKNAME_MYSQL_PASSWORD");
        boolean required = Boolean.parseBoolean(System.getenv("NICKNAME_MYSQL_REQUIRED"));
        if (required) {
            assertTrue(
                    url != null && !url.isBlank() && username != null && password != null,
                    "Required MySQL test credentials are missing");
        } else {
            assumeTrue(
                    url != null && !url.isBlank() && username != null && password != null,
                    "MySQL integration environment is not configured");
        }

        Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("20260323112331"))
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            insertUser(connection, "legacy-a@example.invalid", "기존 사용자", false);
        }

        Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            insertUser(connection, "candidate-a@example.invalid", "강인한 인간 ABC12", false);
            SQLException duplicate =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    insertUser(
                                            connection,
                                            "candidate-b@example.invalid",
                                            "강인한 인간 ABC12",
                                            false));
            assertEquals("23000", duplicate.getSQLState());
            insertUser(connection, "candidate-c@example.invalid", "강인한 인간 ABC13", false);
            insertUser(connection, "verified-a@example.invalid", "강인한 인간 ABC12", true);

            try (PreparedStatement statement =
                    connection.prepareStatement("SELECT nickname FROM users WHERE email = ?")) {
                statement.setString(1, "legacy-a@example.invalid");
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("기존 사용자", rows.getString(1));
                }
            }
        }
    }

    private void insertUser(Connection connection, String email, String nickname, boolean verified)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "INSERT INTO users (email, nickname, status, is_verified, server_name) VALUES (?, ?, 'ACTIVE', ?, ?)")) {
            statement.setString(1, email);
            statement.setString(2, nickname);
            statement.setBoolean(3, verified);
            statement.setString(4, verified ? "류트" : null);
            statement.executeUpdate();
        }
    }
}
