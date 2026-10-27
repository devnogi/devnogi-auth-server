package until.the.eternity.das.token.application;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import until.the.eternity.das.auth.application.AuthService;
import until.the.eternity.das.auth.application.NicknameCandidateService;
import until.the.eternity.das.auth.dto.request.LoginRequest;
import until.the.eternity.das.common.application.KafkaProducerService;
import until.the.eternity.das.common.application.S3Service;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.common.util.JwtUtil;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "NICKNAME_MYSQL_REQUIRED", matches = "true")
class RefreshTokenLengthMySqlTest {
  @DynamicPropertySource
  static void mysqlProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", () -> System.getenv("NICKNAME_MYSQL_URL"));
    properties.add("spring.datasource.username", () -> System.getenv("NICKNAME_MYSQL_USER"));
    properties.add("spring.datasource.password", () -> System.getenv("NICKNAME_MYSQL_PASSWORD"));
    properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    properties.add("spring.flyway.enabled", () -> "true");
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private UserRepository users;
  @Autowired private AuthService auth;
  @Autowired private TokenService tokens;
  @Autowired private JwtUtil jwt;
  @Autowired private BCryptPasswordEncoder passwords;
  @MockitoBean private NicknameCandidateService candidates;
  @MockitoBean private KafkaProducerService events;
  @MockitoBean private S3Service images;

  @Test
  void freshFlywayDatabaseStoresAndRotatesLongJwtFromActualLogin() {
    assertEquals(8192, jdbc.queryForObject("select character_maximum_length "
      + "from information_schema.columns where table_schema=database() "
      + "and table_name='refresh_tokens' and column_name='token'", Integer.class));
    for (String email : new String[] {asciiEmail(210), asciiEmail(255), unicodeEmail(), emojiEmail()}) {
      User user = createUser(email);
      String issued = auth.login(new LoginRequest(email, "LocalPass123!"), "127.0.0.1", "SyntheticLengthTest")
        .refreshToken();
      assertTrue(issued.length() > 512, "expected long JWT for " + email.length() + "-character email");
      assertTrue(issued.length() <= 8192);
      assertEquals(issued, jdbc.queryForObject("select token from refresh_tokens where token=?", String.class, issued));

      String rotated = tokens.refresh(issued).refreshToken();
      assertNotEquals(issued, rotated);
      assertEquals(user.getId(), jwt.getUserIdFromToken(rotated));
      assertEquals(rotated, jdbc.queryForObject("select token from refresh_tokens where token=?", String.class, rotated));
    }
  }

  @Test
  void databaseStoresAndRotatesJwtLargerThanBrowserCookieLimit() {
    User user = createUser(syntheticUnicodeBoundaryEmail());
    String issued = jwt.generateRefreshToken(user);
    assertTrue(issued.length() > 4096, "expected JWT above the former database limit");
    assertTrue(issued.length() <= 8192);
    tokens.saveNewRefreshToken(user.getId(), issued);
    assertEquals(issued, jdbc.queryForObject("select token from refresh_tokens where token=?", String.class, issued));

    String rotated = tokens.refresh(issued).refreshToken();
    assertTrue(rotated.length() > 4096);
    assertNotEquals(issued, rotated);
    assertEquals(rotated, jdbc.queryForObject("select token from refresh_tokens where token=?", String.class, rotated));
    assertEquals(GlobalExceptionCode.INVALID_REFRESH_TOKEN,
      assertThrows(CustomException.class, () -> tokens.refresh(issued)).getCode());
  }

  @Test
  void oldFormatTokenForLongEmailIsConsumedOnceAfterMigration() {
    User user = createUser(asciiEmail(210));
    String oldToken = legacyToken(user);
    assertTrue(oldToken.length() <= 512);
    jdbc.update("insert into refresh_tokens(user_id, token, expires_at, created_at, revoked) "
      + "values (?, ?, date_add(now(), interval 1 day), now(), false)", user.getId(), oldToken);

    String replacement = tokens.refresh(oldToken).refreshToken();
    assertTrue(replacement.length() > 512);
    assertEquals(0, jdbc.queryForObject("select count(*) from refresh_tokens "
      + "where token=? and revoked=false", Integer.class, oldToken));
    assertEquals(GlobalExceptionCode.INVALID_REFRESH_TOKEN,
      assertThrows(CustomException.class, () -> tokens.refresh(oldToken)).getCode());
  }

  private User createUser(String email) {
    Long roleId = jdbc.queryForObject("select id from roles where name='USER'", Long.class);
    String nickname = "합성 " + UUID.randomUUID().toString().substring(0, 12);
    jdbc.update("insert into users(email, password_hash, nickname, status, role_id, is_verified) "
      + "values (?, ?, ?, 'ACTIVE', ?, false)", email, passwords.encode("LocalPass123!"), nickname, roleId);
    Long id = jdbc.queryForObject("select id from users where email=?", Long.class, email);
    return users.findById(id).orElseThrow();
  }

  private String asciiEmail(int length) {
    int lastLabelLength = length - 65 - 60 - 1 - 60 - 1 - 8;
    String email = "u".repeat(48) + UUID.randomUUID().toString().substring(0, 16)
      + "@" + "d".repeat(60) + "." + "e".repeat(60) + "."
      + "f".repeat(lastLabelLength) + ".invalid";
    assertEquals(length, email.length());
    return email;
  }

  private String unicodeEmail() {
    String email = UUID.randomUUID().toString().replace("-", "") + "@"
      + "가".repeat(60) + "." + "나".repeat(60)
      + "." + "다".repeat(60) + "." + "라".repeat(31) + ".invalid";
    assertEquals(255, email.length());
    return email;
  }

  private String emojiEmail() {
    String email = UUID.randomUUID().toString().replace("-", "")
      + "@" + "😀".repeat(214) + ".invalid";
    assertEquals(255, email.codePointCount(0, email.length()));
    return email;
  }

  private String syntheticUnicodeBoundaryEmail() {
    // Keep every supplementary code point escaped to two UTF-16 surrogates by Jackson.
    String unique = UUID.randomUUID().toString().replace("-", "");
    StringBuilder firstLabel = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      firstLabel.appendCodePoint(0x20000 + (Integer.parseInt(unique.substring(i * 4, i * 4 + 4), 16) % 0x1000));
    }
    firstLabel.append(new String(Character.toChars(0x20000)).repeat(20));
    String label = new String(Character.toChars(0x20000)).repeat(24);
    String email = "a@" + firstLabel + "." + (label + ".").repeat(8) + label + ".com";
    assertEquals(255, email.codePointCount(0, email.length()));
    return email;
  }

  private String legacyToken(User user) {
    byte[] key = Base64.getDecoder().decode(
      "bWVtb25vZ2ktbG9jYWwtb25seS1zeW50aGV0aWMta2V5LTIwMjY=");
    Instant now = Instant.now();
    return Jwts.builder()
      .claim("userId", user.getId())
      .claim("type", "REFRESH")
      .subject(user.getEmail())
      .issuer("contract-test")
      .issuedAt(Date.from(now))
      .expiration(Date.from(now.plusSeconds(3600)))
      .signWith(Keys.hmacShaKeyFor(key))
      .compact();
  }
}
