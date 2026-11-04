package until.the.eternity.das.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;

@DataJpaTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "NICKNAME_MYSQL_REQUIRED", matches = "true")
class UserSearchMySqlTest {

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("NICKNAME_MYSQL_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("NICKNAME_MYSQL_USER"));
        properties.add(
                "spring.datasource.password", () -> System.getenv("NICKNAME_MYSQL_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;

    @Test
    void migrationAndRepositoryEnforceTheRoleOrderingAndThousandUserCutoff() {
        assertMigrationAndDescendingIndex();
        String prefix = "user-search-" + UUID.randomUUID().toString().substring(0, 8);

        try {
            insertUser(prefix, "super", 1L, LocalDateTime.of(2099, 12, 31, 23, 59));
            insertUser(prefix, "null-role", null, LocalDateTime.of(2099, 12, 31, 23, 58));
            insertUser(prefix, "admin", 2L, LocalDateTime.of(2099, 12, 31, 23, 57));
            insertDatedUsers(prefix);
            insertUser(prefix, "null-login", 3L, null);
            assertTopThousandQueryUsesDescendingIndex();

            List<User> snapshot = users.findRecentSearchableUsers();
            List<String> nicknames = snapshot.stream().map(User::getNickname).toList();

            assertThat(snapshot).hasSize(1_000);
            assertThat(nicknames.get(0)).isEqualTo(prefix + "-admin");
            assertThat(nicknames.get(999)).isEqualTo(prefix + "-dated-998");
            assertThat(nicknames)
                    .contains(prefix + "-admin", prefix + "-dated-000", prefix + "-dated-998")
                    .doesNotContain(
                            prefix + "-super", prefix + "-null-role", prefix + "-null-login");
            assertThat(snapshot.get(0).getRole().getId()).isEqualTo(2L);
        } finally {
            jdbc.update("DELETE FROM users WHERE email LIKE ?", prefix + "%");
        }
    }

    private void assertMigrationAndDescendingIndex() {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM flyway_schema_history WHERE version='20261104000000' AND success=1",
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                """
                                SELECT COUNT(*) FROM information_schema.statistics
                                WHERE table_schema=DATABASE()
                                  AND table_name='users'
                                  AND index_name='idx_users_last_login_at_desc'
                                  AND column_name='last_login_at'
                                  AND collation='D'
                                """,
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForList(
                                """
                                        SELECT column_name FROM information_schema.statistics
                                        WHERE table_schema=DATABASE()
                                          AND table_name='users'
                                          AND index_name='idx_users_last_login_at_desc'
                                        ORDER BY seq_in_index
                                        """,
                                String.class))
                .containsExactly("last_login_at", "id");
    }

    private void assertTopThousandQueryUsesDescendingIndex() {
        var explain =
                jdbc.queryForMap(
                        """
                        EXPLAIN SELECT u.* FROM users u FORCE INDEX (idx_users_last_login_at_desc)
                        WHERE u.role_id <> 1
                        ORDER BY u.last_login_at DESC, u.id DESC
                        LIMIT 1000
                        """);

        assertThat(explain.get("key")).isEqualTo("idx_users_last_login_at_desc");
        assertThat((String) explain.get("Extra")).doesNotContain("Using filesort");
    }

    private void insertDatedUsers(String prefix) {
        String sql =
                "INSERT INTO users(email,nickname,status,role_id,is_verified,last_login_at) "
                        + "VALUES (?,?,'ACTIVE',3,false,?)";
        jdbc.batchUpdate(
                sql,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement statement, int index)
                            throws SQLException {
                        String suffix = String.format("%03d", index);
                        statement.setString(1, prefix + "-dated-" + suffix + "@example.invalid");
                        statement.setString(2, prefix + "-dated-" + suffix);
                        statement.setTimestamp(
                                3,
                                Timestamp.valueOf(
                                        LocalDateTime.of(2099, 12, 30, 23, 59)
                                                .minusMinutes(index)));
                    }

                    @Override
                    public int getBatchSize() {
                        return 999;
                    }
                });
    }

    private void insertUser(String prefix, String label, Long roleId, LocalDateTime lastLoginAt) {
        jdbc.update(
                "INSERT INTO users(email,nickname,status,role_id,is_verified,last_login_at) "
                        + "VALUES (?,?,'ACTIVE',?,false,?)",
                prefix + "-" + label + "@example.invalid",
                prefix + "-" + label,
                roleId,
                lastLoginAt == null ? null : Timestamp.valueOf(lastLoginAt));
    }
}
