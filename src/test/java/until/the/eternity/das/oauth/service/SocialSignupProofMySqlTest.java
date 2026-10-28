package until.the.eternity.das.oauth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import until.the.eternity.das.auth.application.NicknameCandidateService;
import until.the.eternity.das.auth.dto.request.SocialSignUpRequest;
import until.the.eternity.das.common.application.KafkaProducerService;
import until.the.eternity.das.common.application.S3Service;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.oauth.dto.OauthUserDTO;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "NICKNAME_MYSQL_REQUIRED", matches = "true")
class SocialSignupProofMySqlTest {
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

    @Autowired private OAuthExchangeService exchanges;
    @Autowired private SocialAuthService socialSignup;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private NicknameCandidateService candidates;
    @MockitoBean private KafkaProducerService events;
    @MockitoBean private S3Service images;

    @Test
    void signupUsesOnlyServerVerifiedIdentityAndRejectsDirectOrForgedRequests() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String verifiedId = "verified-" + suffix;
        String verifiedEmail = "verified-" + suffix + "@example.invalid";
        String nickname = "검증된 사용자 " + suffix;
        String binding = exchanges.newBinding();
        String code =
                exchanges.issueSignupCode(
                        new OauthUserDTO(verifiedId, verifiedEmail, "GOOGLE"), binding);
        String proof = exchanges.exchange(code, binding).signupProof();

        SocialSignUpRequest direct =
                new SocialSignUpRequest(
                        "GOOGLE",
                        verifiedId,
                        verifiedEmail,
                        nickname,
                        "nickname-ticket",
                        null,
                        null);
        assertEquals(
                GlobalExceptionCode.OAUTH_SIGNUP_PROOF_REQUIRED,
                assertThrows(
                                CustomException.class,
                                () -> socialSignup.completeSocialSignup(direct, binding))
                        .getCode());

        SocialSignUpRequest forged =
                new SocialSignUpRequest(
                        "GOOGLE",
                        "attacker-id",
                        verifiedEmail,
                        nickname,
                        "nickname-ticket",
                        proof,
                        null);
        assertEquals(
                GlobalExceptionCode.OAUTH_SIGNUP_PROOF_INVALID,
                assertThrows(
                                CustomException.class,
                                () -> socialSignup.completeSocialSignup(forged, binding))
                        .getCode());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE email = ?",
                        Integer.class,
                        verifiedEmail));
        verifyNoInteractions(events, images);

        SocialSignUpRequest valid =
                new SocialSignUpRequest(null, null, null, nickname, "nickname-ticket", proof, null);
        Long userId = socialSignup.completeSocialSignup(valid, binding).id();
        assertTrue(userId > 0);
        assertEquals(
                verifiedEmail,
                jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId));
        assertEquals(
                verifiedId,
                jdbc.queryForObject(
                        "SELECT provider_user_id FROM oauth_users WHERE user_id = ?",
                        String.class,
                        userId));
        assertEquals(
                GlobalExceptionCode.OAUTH_SIGNUP_PROOF_INVALID,
                assertThrows(
                                CustomException.class,
                                () -> socialSignup.completeSocialSignup(valid, binding))
                        .getCode());
    }
}
