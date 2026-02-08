package until.the.eternity.das.auth.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.multipart.MultipartFile;
import until.the.eternity.das.auth.dto.request.LoginRequest;
import until.the.eternity.das.auth.dto.request.SignUpRequest;
import until.the.eternity.das.auth.dto.response.LoginResultResponse;
import until.the.eternity.das.auth.dto.response.SignUpResponse;
import until.the.eternity.das.common.application.S3Service;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.common.util.JwtUtil;
import until.the.eternity.das.login.entity.AccountLock;
import until.the.eternity.das.login.entity.AccountLockRepository;
import until.the.eternity.das.login.entity.LoginHistoryRepository;
import until.the.eternity.das.role.entity.Role;
import until.the.eternity.das.role.entity.RoleRepository;
import until.the.eternity.das.role.entity.enums.Name;
import until.the.eternity.das.token.application.TokenService;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @InjectMocks
  private AuthService authService;

  @Mock
  private AuthConverter authConverter;
  @Mock
  private UserRepository userRepository;
  @Mock
  private RoleRepository roleRepository;
  @Mock
  private BCryptPasswordEncoder bCryptPasswordEncoder;
  @Mock
  private JwtUtil jwtUtil;
  @Mock
  private TokenService tokenService;
  @Mock
  private S3Service s3Service;
  @Mock
  private AccountLockRepository accountLockRepository;
  @Mock
  private LoginHistoryRepository loginHistoryRepository;

  private SignUpRequest signUpRequest;
  private LoginRequest loginRequest;
  private User user;
  private Role userRole;
  private MockMultipartFile mockFile; // MockMultipartFile 추가

  @BeforeEach
  void setUp() {
    // 테스트용 MockMultipartFile 생성
    mockFile = new MockMultipartFile(
      "file", // DTO의 필드명과 일치
      "profile.jpg",
      "image/jpeg",
      "test image content".getBytes()
    );

    // SignUpRequest에 mockFile 추가, 비밀번호 형식 준수
    signUpRequest = new SignUpRequest("test@test.com", "password123!", "testuser", mockFile);
    loginRequest = new LoginRequest("test@test.com", "password123!");
    userRole = Role.builder()
      .id(1L)
      .name(Name.USER)
      .build();
    user = User.builder()
      .id(1L)
      .email(signUpRequest.email())
      .passwordHash("encodedPassword")
      .nickname(signUpRequest.nickname())
      .profileImageUrl("mocked-profile-image-url")
      .build();
  }

  @Test
  @DisplayName("회원가입 성공 테스트")
  void signUp_Success() {
    // given
    String encodedPassword = "encodedPassword";
    String profileImageUrl = "mocked-profile-image-url";

    // 역할(Role) 조회 Mocking
    when(roleRepository.findByName(Name.USER)).thenReturn(Optional.of(userRole));
    // 이메일 및 닉네임 중복 없음 Mocking
    when(userRepository.existsByEmail(signUpRequest.email())).thenReturn(false);
    when(userRepository.existsByNickname(signUpRequest.nickname())).thenReturn(false);
    // S3 이미지 업로드 Mocking
    when(s3Service.uploadImage(any(MultipartFile.class), anyString())).thenReturn(profileImageUrl);
    // 비밀번호 인코딩 Mocking
    when(bCryptPasswordEncoder.encode(signUpRequest.password())).thenReturn(encodedPassword);
    // DTO -> User 변환 Mocking
    when(
      authConverter.fromUserSignUpRequestToUser(signUpRequest, encodedPassword, userRole, profileImageUrl)).thenReturn(
      user);
    // User 저장 Mocking
    when(userRepository.save(any(User.class))).thenReturn(user);
    // User -> SignUpResponse 변환 Mocking
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
    // USER Role은 존재해야 이메일 중복 검사 로직까지 도달함
    when(roleRepository.findByName(Name.USER)).thenReturn(Optional.of(userRole));
    when(userRepository.existsByEmail(signUpRequest.email())).thenReturn(true);

    // when & then
    CustomException exception = assertThrows(CustomException.class, () -> authService.signUpUser(signUpRequest));
    assertEquals(GlobalExceptionCode.EMAIL_ALREADY_EXISTS, exception.getCode());
  }

  @Test
  @DisplayName("회원가입 실패 - 닉네임 중복")
  void signUp_Fail_NicknameAlreadyExists() {
    // given
    when(roleRepository.findByName(Name.USER)).thenReturn(Optional.of(userRole));
    when(userRepository.existsByEmail(signUpRequest.email())).thenReturn(false); // 이메일은 중복 아님
    when(userRepository.existsByNickname(signUpRequest.nickname())).thenReturn(true); // 닉네임 중복

    // when & then
    CustomException exception = assertThrows(CustomException.class, () -> authService.signUpUser(signUpRequest));
    assertEquals(GlobalExceptionCode.NICKNAME_ALREADY_EXISTS, exception.getCode());
  }


  @Test
  @DisplayName("로그인 성공 테스트")
  void login_Success() {
    // given
    AccountLock accountLock = AccountLock.builder()
      .user(user)
      .userId(user.getId())
      .failedAttempts(0)
      .updatedAt(LocalDateTime.now())
      .build();

    when(userRepository.findByEmail(loginRequest.email())).thenReturn(Optional.of(user));
    when(accountLockRepository.findById(user.getId())).thenReturn(Optional.of(accountLock));
    when(bCryptPasswordEncoder.matches(loginRequest.password(), user.getPasswordHash())).thenReturn(true);
    when(jwtUtil.generateAccessToken(user)).thenReturn("access-token");
    when(jwtUtil.generateRefreshToken(user)).thenReturn("refresh-token");
    doNothing().when(tokenService)
      .saveNewRefreshToken(user.getId(), "refresh-token");

    // when
    LoginResultResponse response = authService.login(loginRequest, "127.0.0.1", "TestAgent");

    // then
    assertNotNull(response);
    assertEquals("access-token", response.accessToken());
    assertEquals("refresh-token", response.refreshToken());
  }

  // 로그인 실패 테스트는 변경된 부분이 없으므로 그대로 유지됩니다.
  @Test
  @DisplayName("로그인 실패 - 사용자를 찾을 수 없음")
  void login_Fail_UserNotFound() {
    // given
    when(userRepository.findByEmail(loginRequest.email())).thenReturn(Optional.empty());

    // when & then
    CustomException exception = assertThrows(CustomException.class,
      () -> authService.login(loginRequest, "127.0.0.1", "TestAgent"));
    assertEquals(GlobalExceptionCode.USER_NOT_EXISTS, exception.getCode());
  }

  @Test
  @DisplayName("로그인 실패 - 잘못된 비밀번호")
  void login_Fail_InvalidPassword() {
    // given
    AccountLock accountLock = AccountLock.builder()
      .user(user)
      .userId(user.getId())
      .failedAttempts(0)
      .updatedAt(LocalDateTime.now())
      .build();

    when(userRepository.findByEmail(loginRequest.email())).thenReturn(Optional.of(user));
    when(accountLockRepository.findById(user.getId())).thenReturn(Optional.of(accountLock));
    when(bCryptPasswordEncoder.matches(loginRequest.password(), user.getPasswordHash())).thenReturn(false);

    // when & then
    CustomException exception = assertThrows(CustomException.class,
      () -> authService.login(loginRequest, "127.0.0.1", "TestAgent"));
    assertEquals(GlobalExceptionCode.INVALID_PASSWORD, exception.getCode());
  }
}