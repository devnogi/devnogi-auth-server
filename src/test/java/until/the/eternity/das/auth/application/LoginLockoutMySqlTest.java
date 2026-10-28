package until.the.eternity.das.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import until.the.eternity.das.auth.dto.request.LoginRequest;
import until.the.eternity.das.common.application.KafkaProducerService;
import until.the.eternity.das.common.application.S3Service;
import until.the.eternity.das.common.constant.JwtConstant;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.common.util.JwtUtil;
import until.the.eternity.das.token.application.TokenService;
import until.the.eternity.das.user.entity.User;

/** Uses the real transaction proxy and committed MySQL state between requests. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "NICKNAME_MYSQL_REQUIRED", matches = "true")
class LoginLockoutMySqlTest {
    private static final String PASSWORD = "LocalPass123!";
    private static final String WRONG_PASSWORD = "WrongPass123!";

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
    @Autowired private JwtConstant jwtSettings;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @MockitoSpyBean private JwtUtil jwt;
    @MockitoSpyBean private TokenService tokenService;
    @MockitoBean private NicknameCandidateService candidates;
    @MockitoBean private KafkaProducerService events;
    @MockitoBean private S3Service images;

    @Test
    void fiveFailuresAndLockedRequestCommitHistoryThenExpiredLockResetsOnSuccess()
            throws Exception {
        Account account = createAccount();
        for (int attempt = 1; attempt <= 5; attempt++) {
            var response =
                    mvc.perform(
                                    post("/api/auth/login")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    mapper.writeValueAsBytes(
                                                            new LoginRequest(
                                                                    account.email(),
                                                                    WRONG_PASSWORD))))
                            .andExpect(status().isBadRequest())
                            .andExpect(
                                    jsonPath("$.code")
                                            .value(GlobalExceptionCode.INVALID_PASSWORD.getCode()))
                            .andReturn()
                            .getResponse();
            assertThat(response.getHeaders("Set-Cookie")).isEmpty();
            assertThat(
                            number(
                                    "select failed_attempts from account_locks where user_id=?",
                                    account.id()))
                    .isEqualTo(attempt);
            assertThat(
                            number(
                                    "select count(*) from login_history where user_id=? and reason='WRONG_PASSWORD'",
                                    account.id()))
                    .isEqualTo(attempt);
        }
        assertThat(
                        number(
                                "select count(*) from account_locks where user_id=? and locked_until > now()",
                                account.id()))
                .isEqualTo(1);
        var lockedUntil =
                jdbc.queryForObject(
                        "select locked_until from account_locks where user_id=?",
                        java.sql.Timestamp.class,
                        account.id());

        var locked =
                mvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                mapper.writeValueAsBytes(
                                                        new LoginRequest(
                                                                account.email(), PASSWORD))))
                        .andExpect(status().isUnauthorized())
                        .andExpect(
                                jsonPath("$.code")
                                        .value(GlobalExceptionCode.ACCOUNT_LOCKED.getCode()))
                        .andReturn()
                        .getResponse();
        assertThat(locked.getHeaders("Set-Cookie")).isEmpty();
        assertThat(
                        number(
                                "select failed_attempts from account_locks where user_id=?",
                                account.id()))
                .isEqualTo(5);
        assertThat(
                        jdbc.queryForObject(
                                "select locked_until from account_locks where user_id=?",
                                java.sql.Timestamp.class,
                                account.id()))
                .isEqualTo(lockedUntil);
        assertThat(
                        number(
                                "select count(*) from login_history where user_id=? and reason='LOCKED_ACCOUNT'",
                                account.id()))
                .isEqualTo(1);
        assertThat(number("select count(*) from refresh_tokens where user_id=?", account.id()))
                .isZero();

        jdbc.update(
                "update account_locks set locked_until=date_sub(now(), interval 1 second) where user_id=?",
                account.id());
        var success =
                mvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                mapper.writeValueAsBytes(
                                                        new LoginRequest(
                                                                account.email(), PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse();
        assertThat(success.getHeaders("Set-Cookie")).hasSize(2);
        assertThat(
                        number(
                                "select count(*) from account_locks where user_id=? and failed_attempts=0 and locked_until is null",
                                account.id()))
                .isEqualTo(1);
        assertThat(
                        number(
                                "select count(*) from login_history where user_id=? and success=true",
                                account.id()))
                .isEqualTo(1);
        assertThat(
                        number(
                                "select count(*) from refresh_tokens where user_id=? and revoked=false",
                                account.id()))
                .isEqualTo(1);
    }

    @Test
    void concurrentFailuresCommitWithoutLostUpdatesForNewAndExistingLocks() throws Exception {
        for (boolean existingLock : new boolean[] {false, true}) {
            for (int iteration = 0; iteration < 5; iteration++) {
                Account account = createAccount();
                if (existingLock) {
                    jdbc.update(
                            "insert into account_locks(user_id,failed_attempts,updated_at) values (?,0,now())",
                            account.id());
                }
                CountDownLatch ready = new CountDownLatch(5);
                CountDownLatch start = new CountDownLatch(1);
                try (var pool = Executors.newFixedThreadPool(5)) {
                    var attempts =
                            new java.util.ArrayList<
                                    java.util.concurrent.Future<GlobalExceptionCode>>();
                    for (int i = 0; i < 5; i++) {
                        attempts.add(
                                pool.submit(
                                        () -> {
                                            ready.countDown();
                                            if (!start.await(10, TimeUnit.SECONDS))
                                                throw new IllegalStateException(
                                                        "login start timeout");
                                            return (GlobalExceptionCode)
                                                    assertThrows(
                                                                    LoginRejectedException.class,
                                                                    () ->
                                                                            auth.login(
                                                                                    new LoginRequest(
                                                                                            account
                                                                                                    .email(),
                                                                                            WRONG_PASSWORD),
                                                                                    "127.0.0.1",
                                                                                    "lockout-test"))
                                                            .getCode();
                                        }));
                    }
                    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                    start.countDown();
                    for (var attempt : attempts) {
                        assertThat(attempt.get(45, TimeUnit.SECONDS))
                                .isEqualTo(GlobalExceptionCode.INVALID_PASSWORD);
                    }
                }
                assertThat(
                                number(
                                        "select count(*) from account_locks where user_id=? and failed_attempts=5 and locked_until > now()",
                                        account.id()))
                        .isEqualTo(1);
                assertThat(
                                number(
                                        "select count(*) from login_history where user_id=? and reason='WRONG_PASSWORD'",
                                        account.id()))
                        .isEqualTo(5);
            }
        }
    }

    @Test
    void mysqlHistoryInsertFailureRollsBackNewLockAndDoesNotBecomeLoginRejection() {
        Account account = createAccount();
        RuntimeException error =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                auth.login(
                                        new LoginRequest(account.email(), WRONG_PASSWORD),
                                        "x".repeat(100),
                                        "lockout-test"));
        assertThat(error).isNotInstanceOf(LoginRejectedException.class);
        assertThat(number("select count(*) from account_locks where user_id=?", account.id()))
                .isZero();
        assertThat(number("select count(*) from login_history where user_id=?", account.id()))
                .isZero();
    }

    @Test
    void mysqlRefreshTokenInsertFailureRollsBackSuccessHistoryAndLockReset() {
        Account account = createAccount();
        jdbc.update(
                "insert into account_locks(user_id,failed_attempts,updated_at) values (?,2,now())",
                account.id());
        String oversizedToken =
                Jwts.builder()
                        .subject("x".repeat(7000))
                        .expiration(Date.from(Instant.now().plusSeconds(3600)))
                        .signWith(
                                Keys.hmacShaKeyFor(
                                        Decoders.BASE64.decode(jwtSettings.getSecretKey())))
                        .compact();
        assertThat(oversizedToken.length()).isGreaterThan(8192);
        doReturn(oversizedToken).when(jwt).generateRefreshToken(any(User.class));

        RuntimeException error =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                auth.login(
                                        new LoginRequest(account.email(), PASSWORD),
                                        "127.0.0.1",
                                        "lockout-test"));
        assertThat(error).isNotInstanceOf(LoginRejectedException.class);
        assertThat(
                        number(
                                "select failed_attempts from account_locks where user_id=?",
                                account.id()))
                .isEqualTo(2);
        assertThat(number("select count(*) from login_history where user_id=?", account.id()))
                .isZero();
        assertThat(number("select count(*) from refresh_tokens where user_id=?", account.id()))
                .isZero();
        assertThat(
                        number(
                                "select count(*) from users where id=? and last_login_at is not null",
                                account.id()))
                .isZero();
    }

    @Test
    void unrelatedCustomExceptionDuringTokenStorageAlsoRollsBack() {
        Account account = createAccount();
        jdbc.update(
                "insert into account_locks(user_id,failed_attempts,updated_at) values (?,2,now())",
                account.id());
        doThrow(new CustomException(GlobalExceptionCode.INVALID_REFRESH_TOKEN))
                .when(tokenService)
                .saveNewRefreshToken(anyLong(), anyString());

        CustomException error =
                assertThrows(
                        CustomException.class,
                        () ->
                                auth.login(
                                        new LoginRequest(account.email(), PASSWORD),
                                        "127.0.0.1",
                                        "lockout-test"));
        assertThat(error.getCode()).isEqualTo(GlobalExceptionCode.INVALID_REFRESH_TOKEN);
        assertThat(
                        number(
                                "select failed_attempts from account_locks where user_id=?",
                                account.id()))
                .isEqualTo(2);
        assertThat(number("select count(*) from login_history where user_id=?", account.id()))
                .isZero();
        assertThat(number("select count(*) from refresh_tokens where user_id=?", account.id()))
                .isZero();
        assertThat(
                        number(
                                "select count(*) from users where id=? and last_login_at is not null",
                                account.id()))
                .isZero();
    }

    private Account createAccount() {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        String email = "lockout-" + suffix + "@example.invalid";
        jdbc.update(
                "insert into users(email,password_hash,nickname,status,role_id,is_verified) values (?,?,?,'ACTIVE',3,false)",
                email,
                passwords.encode(PASSWORD),
                "잠금 " + suffix);
        return new Account(
                jdbc.queryForObject("select id from users where email=?", Long.class, email),
                email);
    }

    private int number(String sql, Long id) {
        return jdbc.queryForObject(sql, Integer.class, id);
    }

    private record Account(Long id, String email) {}
}
