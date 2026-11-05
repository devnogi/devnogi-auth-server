package until.the.eternity.das.oauth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import until.the.eternity.das.auth.application.AuthConverter;
import until.the.eternity.das.auth.application.NicknameCandidateService;
import until.the.eternity.das.auth.dto.request.SocialSignUpRequest;
import until.the.eternity.das.common.application.KafkaProducerService;
import until.the.eternity.das.common.application.S3Service;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.common.util.JwtUtil;
import until.the.eternity.das.oauth.dto.OauthUserDTO;
import until.the.eternity.das.oauth.entity.OauthUser;
import until.the.eternity.das.oauth.entity.OauthUserRepository;
import until.the.eternity.das.role.entity.Role;
import until.the.eternity.das.role.entity.RoleRepository;
import until.the.eternity.das.role.entity.enums.Name;
import until.the.eternity.das.token.application.TokenService;
import until.the.eternity.das.user.application.GameProfileService;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;
import until.the.eternity.das.user.entity.enums.Status;
import until.the.eternity.das.user.outbox.UserInfoOutboxService;

@ExtendWith(MockitoExtension.class)
class SocialAuthNicknameTest {
    @Mock private UserRepository users;
    @Mock private RoleRepository roles;
    @Mock private OauthUserRepository oauthUsers;
    @Mock private AuthConverter converter;
    @Mock private S3Service images;
    @Mock private JwtUtil jwt;
    @Mock private TokenService tokens;
    @Mock private KafkaProducerService events;
    @Mock private UserInfoOutboxService outbox;
    @Mock private NicknameCandidateService candidates;
    @Mock private OAuthExchangeService exchange;
    @InjectMocks private SocialAuthService service;
    @Mock private GameProfileService gameProfileService;

    @Test
    void socialSignupEnqueuesSnapshotWithoutSendingKafkaOnRequestThread() {
        SocialSignUpRequest request =
                new SocialSignUpRequest(
                        "google",
                        "provider-id",
                        "social@example.invalid",
                        "generatedName",
                        "ticket",
                        "proof",
                        null);
        OauthUserDTO identity = new OauthUserDTO("provider-id", "social@example.invalid", "GOOGLE");
        Role role = Role.builder().id(3L).name(Name.USER).build();
        User user =
                User.builder()
                        .id(99L)
                        .email(identity.getEmail())
                        .nickname(request.nickname())
                        .status(Status.ACTIVE)
                        .role(role)
                        .build();
        when(exchange.consumeSignupProof("proof", "binding")).thenReturn(identity);
        when(roles.findByName(Name.USER)).thenReturn(Optional.of(role));
        when(converter.fromOauthUserDTOToUser(identity, request.nickname(), role, null))
                .thenReturn(user);

        assertEquals(99L, service.completeSocialSignup(request, "binding").id());

        verify(users).saveAndFlush(user);
        verify(oauthUsers).save(org.mockito.ArgumentMatchers.any(OauthUser.class));
        verify(outbox).enqueue(user);
        verifyNoInteractions(events);
    }

    @Test
    void socialSignupRejectsManualNicknameBeforeDatabaseOrKafka() {
        SocialSignUpRequest request =
                new SocialSignUpRequest(
                        "google",
                        "synthetic-id",
                        "social@example.invalid",
                        "manualName",
                        null,
                        "synthetic-proof",
                        null);
        when(exchange.consumeSignupProof("synthetic-proof", "synthetic-binding"))
                .thenReturn(new OauthUserDTO("synthetic-id", "social@example.invalid", "GOOGLE"));
        doThrow(new CustomException(GlobalExceptionCode.INVALID_NICKNAME_TICKET))
                .when(candidates)
                .validateForChange(request.nickname(), request.nicknameTicket());

        assertEquals(
                GlobalExceptionCode.INVALID_NICKNAME_TICKET,
                assertThrows(
                                CustomException.class,
                                () -> service.completeSocialSignup(request, "synthetic-binding"))
                        .getCode());
        verifyNoInteractions(users, roles, oauthUsers, converter, images, events);
    }

    @Test
    void missingProofRejectsSignupBeforeAnySideEffect() {
        SocialSignUpRequest request =
                new SocialSignUpRequest(
                        "google",
                        "synthetic-id",
                        "social@example.invalid",
                        "generatedName",
                        "ticket",
                        null,
                        null);
        doThrow(new CustomException(GlobalExceptionCode.OAUTH_SIGNUP_PROOF_REQUIRED))
                .when(exchange)
                .consumeSignupProof(null, null);

        assertEquals(
                GlobalExceptionCode.OAUTH_SIGNUP_PROOF_REQUIRED,
                assertThrows(
                                CustomException.class,
                                () -> service.completeSocialSignup(request, null))
                        .getCode());
        verifyNoInteractions(users, roles, oauthUsers, converter, images, events, candidates);
    }

    @Test
    void clientCannotReplaceProviderIdentityOrEmail() {
        SocialSignUpRequest request =
                new SocialSignUpRequest(
                        "google",
                        "forged-id",
                        "forged@example.invalid",
                        "generatedName",
                        "ticket",
                        "proof",
                        null);
        when(exchange.consumeSignupProof("proof", "binding"))
                .thenReturn(new OauthUserDTO("verified-id", "social@example.invalid", "GOOGLE"));

        assertEquals(
                GlobalExceptionCode.OAUTH_SIGNUP_PROOF_INVALID,
                assertThrows(
                                CustomException.class,
                                () -> service.completeSocialSignup(request, "binding"))
                        .getCode());
        verifyNoInteractions(users, roles, oauthUsers, converter, images, events, candidates);
    }
}
