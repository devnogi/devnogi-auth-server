package until.the.eternity.das.auth.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import until.the.eternity.das.auth.dto.request.LoginRequest;
import until.the.eternity.das.auth.dto.request.SignUpRequest;
import until.the.eternity.das.auth.dto.response.LoginResultResponse;
import until.the.eternity.das.auth.dto.response.SignUpResponse;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.common.util.JwtUtil;
import until.the.eternity.das.role.entity.Role;
import until.the.eternity.das.role.entity.RoleRepository;
import until.the.eternity.das.role.entity.enums.Name;
import until.the.eternity.das.token.application.TokenService;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @InjectMocks
  private AuthService authService;

  @Mock
  private UserRepository userRepository;

  @Mock
  private RoleRepository roleRepository;

  @Mock
  private BCryptPasswordEncoder bCryptPasswordEncoder;

  @Mock
  private AuthConverter authConverter;

  @Mock
  private JwtUtil jwtUtil;

  @Mock
  private TokenService tokenService;

  private SignUpRequest signUpRequest;
  private LoginRequest loginRequest;
  private User user;
  private Role userRole;

  @BeforeEach
  void setUp() {
    signUpRequest = new SignUpRequest("test@test.com", "password123", "testuser");
    loginRequest = new LoginRequest("test@test.com", "password123");
    userRole = Role.builder()
      .id(1L)
      .name(Name.USER)
      .build();
    user = User.builder()
      .id(1L)
      .email(signUpRequest.email())
      .passwordHash("encodedPassword")
      .nickname(signUpRequest.nickname())
      .build();
  }

  @Test
  @DisplayName("회원가입 성공 테스트")
  void signUp_Success() {
    // given
    when(userRepository.existsByEmail(signUpRequest.email())).thenReturn(false);
    when(roleRepository.findByName(Name.USER)).thenReturn(Optional.of(userRole));
    when(authConverter.fromUserSignUpRequestToUser(any(SignUpRequest.class), any(PasswordEncoder.class).toString(),
      any(Role.class))).thenReturn(user);
    when(userRepository.save(any(User.class))).thenReturn(user);
    when(authConverter.fromUserToUserSignUpResponse(any(User.class))).thenReturn(
      SignUpResponse.builder()
        .id(user.getId())
        .build()
    );

    // when
    SignUpResponse response = authService.signUpUser(signUpRequest);

    // then
    assertNotNull(response);
    assertEquals(user.getId(), response.id());
  }

  @Test
  @DisplayName("회원가입 실패 - 이메일 중복")
  void signUp_Fail_EmailAlreadyExists() {
    // given
    when(userRepository.existsByEmail(signUpRequest.email())).thenReturn(true);

    // when & then
    CustomException exception = assertThrows(CustomException.class, () -> authService.signUpUser(signUpRequest));
    assertEquals(GlobalExceptionCode.EMAIL_ALREADY_EXISTS, exception.getCode());
  }

  @Test
  @DisplayName("로그인 성공 테스트")
  void login_Success() {
    // given
    when(userRepository.findByEmail(loginRequest.email())).thenReturn(Optional.of(user));
    when(bCryptPasswordEncoder.matches(loginRequest.password(), user.getPasswordHash())).thenReturn(true);
    when(jwtUtil.generateAccessToken(user)).thenReturn("access-token");
    when(jwtUtil.generateRefreshToken(user)).thenReturn("refresh-token");
    doNothing().when(tokenService)
      .saveNewRefreshToken(user.getId(), "refresh-token");

    // when
    LoginResultResponse response = authService.login(loginRequest);

    // then
    assertNotNull(response);
    assertEquals("access-token", response.accessToken());
    assertEquals("refresh-token", response.refreshToken());
  }

  @Test
  @DisplayName("로그인 실패 - 사용자를 찾을 수 없음")
  void login_Fail_UserNotFound() {
    // given
    when(userRepository.findByEmail(loginRequest.email())).thenReturn(Optional.empty());

    // when & then
    CustomException exception = assertThrows(CustomException.class, () -> authService.login(loginRequest));
    assertEquals(GlobalExceptionCode.USER_NOT_EXISTS, exception.getCode());
  }

  @Test
  @DisplayName("로그인 실패 - 잘못된 비밀번호")
  void login_Fail_InvalidPassword() {
    // given
    when(userRepository.findByEmail(loginRequest.email())).thenReturn(Optional.of(user));
    when(bCryptPasswordEncoder.matches(loginRequest.password(), user.getPasswordHash())).thenReturn(false);

    // when & then
    CustomException exception = assertThrows(CustomException.class, () -> authService.login(loginRequest));
    assertEquals(GlobalExceptionCode.INVALID_PASSWORD, exception.getCode());
  }
}