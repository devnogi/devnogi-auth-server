package until.the.eternity.das.auth.application;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import until.the.eternity.das.auth.dto.request.LoginRequest;
import until.the.eternity.das.auth.dto.request.SignUpRequest;
import until.the.eternity.das.common.application.KafkaProducerService;
import until.the.eternity.das.common.application.S3Service;
import until.the.eternity.das.common.constant.JwtConstant;
import until.the.eternity.das.common.util.JwtUtil;
import until.the.eternity.das.login.entity.AccountLockRepository;
import until.the.eternity.das.login.entity.LoginHistoryRepository;
import until.the.eternity.das.role.entity.Role;
import until.the.eternity.das.role.entity.enums.Name;
import until.the.eternity.das.role.infrastructure.RoleRepositoryImpl;
import until.the.eternity.das.token.application.TokenService;
import until.the.eternity.das.token.entity.RefreshTokenRepository;
import until.the.eternity.das.user.entity.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
  "jwt.secret-key=bWVtb25vZ2ktbG9jYWwtb25seS1zeW50aGV0aWMta2V5LTIwMjY=",
  "jwt.access-token-validity=3600000",
  "jwt.refresh-token-validity=86400000"
})
@Import({AuthService.class, AuthConverter.class, TokenService.class, JwtUtil.class, RoleRepositoryImpl.class,
  FreshAccountLoginJpaTest.Configuration.class})
class FreshAccountLoginJpaTest {

  @TestConfiguration
  @EnableConfigurationProperties(JwtConstant.class)
  static class Configuration {
    @Bean
    BCryptPasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder();
    }
  }

  @Autowired private AuthService authService;
  @Autowired private UserRepository users;
  @Autowired private AccountLockRepository accountLocks;
  @Autowired private LoginHistoryRepository loginHistory;
  @Autowired private RefreshTokenRepository refreshTokens;
  @Autowired private EntityManager entityManager;
  @Autowired private JwtUtil jwtUtil;
  @MockitoBean private S3Service s3Service;
  @MockitoBean private KafkaProducerService kafkaProducerService;

  @Test
  void firstLoginCreatesAccountLockAndPersistsLoginStateWithoutSeededLock() {
    entityManager.persist(Role.builder().name(Name.USER).build());
    Long userId = authService.signUpUser(new SignUpRequest(
      "fresh@example.test", "LocalPass123!", "freshuser", null)).id();
    entityManager.flush();
    entityManager.clear();
    assertThat(accountLocks.findById(userId)).isEmpty();

    var result = authService.login(
      new LoginRequest("fresh@example.test", "LocalPass123!"), "127.0.0.1", "JpaRegression");
    entityManager.flush();
    entityManager.clear();

    assertThat(accountLocks.findById(userId)).hasValueSatisfying(lock -> {
      assertThat(lock.getUserId()).isEqualTo(userId);
      assertThat(lock.getFailedAttempts()).isZero();
      assertThat(lock.getLockedUntil()).isNull();
    });
    assertThat(loginHistory.count()).isEqualTo(1);
    assertThat(refreshTokens.findByTokenAndRevokedFalse(result.refreshToken())).isPresent();
    assertThat(users.findById(userId)).hasValueSatisfying(user ->
      assertThat(user.getLastLoginAt()).isNotNull());
    assertThat(jwtUtil.validateToken(result.accessToken())).isTrue();
    assertThat(jwtUtil.validateToken(result.refreshToken())).isTrue();

    authService.login(new LoginRequest("fresh@example.test", "LocalPass123!"), "127.0.0.1", "JpaRegression");
    entityManager.flush();
    entityManager.clear();
    assertThat(accountLocks.count()).isEqualTo(1);
    assertThat(loginHistory.count()).isEqualTo(2);
    assertThat(refreshTokens.count()).isEqualTo(2);
  }
}
