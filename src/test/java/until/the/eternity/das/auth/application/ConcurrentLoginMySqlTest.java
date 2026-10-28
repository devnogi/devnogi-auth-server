package until.the.eternity.das.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import until.the.eternity.das.auth.dto.request.LoginRequest;
import until.the.eternity.das.common.application.KafkaProducerService;
import until.the.eternity.das.common.application.S3Service;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "NICKNAME_MYSQL_REQUIRED", matches = "true")
class ConcurrentLoginMySqlTest {
    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("NICKNAME_MYSQL_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("NICKNAME_MYSQL_USER"));
        properties.add(
                "spring.datasource.password", () -> System.getenv("NICKNAME_MYSQL_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private AuthService auth;
    @Autowired private BCryptPasswordEncoder passwords;
    @MockitoBean private NicknameCandidateService candidates;
    @MockitoBean private KafkaProducerService events;
    @MockitoBean private S3Service images;

    @Test
    void simultaneousFirstLoginsCreateOneLockAndBothSucceed() throws Exception {
        assertConcurrentLogins(false);
    }

    @Test
    void simultaneousLoginsWithExistingLockBothSucceed() throws Exception {
        assertConcurrentLogins(true);
    }

    private void assertConcurrentLogins(boolean existingLock) throws Exception {
        String hash = passwords.encode("LocalPass123!");
        for (int iteration = 0; iteration < 5; iteration++) {
            String suffix = UUID.randomUUID().toString().substring(0, 12);
            String email = "concurrent-" + suffix + "@example.invalid";
            jdbc.update(
                    "insert into users(email,password_hash,nickname,status,role_id,is_verified) "
                            + "values (?,?,?,'ACTIVE',3,false)",
                    email,
                    hash,
                    "동시 " + suffix);
            Long id = jdbc.queryForObject("select id from users where email=?", Long.class, email);
            if (existingLock) {
                jdbc.update(
                        "insert into account_locks(user_id,failed_attempts,updated_at) values (?,2,now())",
                        id);
            }

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                var first = pool.submit(() -> login(ready, start, email));
                var second = pool.submit(() -> login(ready, start, email));
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                start.countDown();
                String firstToken = first.get(30, TimeUnit.SECONDS);
                String secondToken = second.get(30, TimeUnit.SECONDS);
                assertNotEquals(firstToken, secondToken);
            }

            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "select count(*) from account_locks where user_id=?",
                            Integer.class,
                            id));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "select failed_attempts from account_locks where user_id=?",
                            Integer.class,
                            id));
            assertEquals(
                    2,
                    jdbc.queryForObject(
                            "select count(*) from login_history where user_id=? and success=true",
                            Integer.class,
                            id));
            assertEquals(
                    2,
                    jdbc.queryForObject(
                            "select count(*) from refresh_tokens where user_id=? and revoked=false",
                            Integer.class,
                            id));
        }
    }

    private String login(CountDownLatch ready, CountDownLatch start, String email)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS))
            throw new IllegalStateException("login start timeout");
        return auth.login(
                        new LoginRequest(email, "LocalPass123!"),
                        "127.0.0.1",
                        "ConcurrentLoginTest")
                .refreshToken();
    }
}
