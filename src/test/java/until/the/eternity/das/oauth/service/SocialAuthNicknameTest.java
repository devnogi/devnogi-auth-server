package until.the.eternity.das.oauth.service;

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
import until.the.eternity.das.oauth.entity.OauthUserRepository;
import until.the.eternity.das.role.entity.RoleRepository;
import until.the.eternity.das.token.application.TokenService;
import until.the.eternity.das.user.entity.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

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
  @Mock private NicknameCandidateService candidates;
  @InjectMocks private SocialAuthService service;

  @Test
  void socialSignupRejectsManualNicknameBeforeDatabaseOrKafka() {
    SocialSignUpRequest request = new SocialSignUpRequest(
      "google", "synthetic-id", "social@example.invalid", "manualName", null, null);
    doThrow(new CustomException(GlobalExceptionCode.INVALID_NICKNAME_TICKET))
      .when(candidates).validateForChange(request.nickname(), request.nicknameTicket());

    assertEquals(GlobalExceptionCode.INVALID_NICKNAME_TICKET,
      assertThrows(CustomException.class, () -> service.completeSocialSignup(request)).getCode());
    verifyNoInteractions(users, roles, oauthUsers, converter, images, events);
  }
}
