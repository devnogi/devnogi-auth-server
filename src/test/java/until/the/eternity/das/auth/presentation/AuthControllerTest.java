package until.the.eternity.das.auth.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import until.the.eternity.das.auth.application.AuthService;
import until.the.eternity.das.auth.dto.request.LoginRequest;
import until.the.eternity.das.auth.dto.request.SignUpRequest;
import until.the.eternity.das.auth.dto.response.LoginResultResponse;
import until.the.eternity.das.auth.dto.response.SignUpResponse;
import until.the.eternity.das.common.config.TestSecurityConfig;
import until.the.eternity.das.common.constant.JwtConstant;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.common.util.CookieUtil;
import until.the.eternity.das.common.util.JwtUtil;
import until.the.eternity.das.oauth.service.SocialAuthService;
import until.the.eternity.das.role.entity.Role;
import until.the.eternity.das.role.entity.enums.Name;
import until.the.eternity.das.token.application.TokenService;
import until.the.eternity.das.user.application.UserService;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
class AuthControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private AuthService authService;

  @MockitoBean
  private TokenService tokenService;

  @MockitoBean
  private SocialAuthService socialAuthService;

  @MockitoBean
  private CookieUtil cookieUtil;

  @MockitoBean
  private JwtUtil jwtUtil;

  @MockitoBean
  private JwtConstant jwtConstant;

  @MockitoBean
  private UserRepository userRepository;

  @MockitoBean
  private UserService userService;

  private MockMultipartFile mockFile;
  private SignUpRequest signUpRequest;
  private LoginRequest loginRequest;
  private User user;
  private Role userRole;

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
  @DisplayName("회원가입 API 성공 테스트")
  void signUpApi_Success() throws Exception {
    // given
    SignUpResponse response = SignUpResponse.builder()
      .id(1L)
      .build();

    when(authService.signUpUser(any(SignUpRequest.class))).thenReturn(response);

    // when & then
    mockMvc.perform(multipart("/api/auth/signup")
        .file(mockFile)
        .param("email", "test@test.com")
        .param("password", "password123!")
        .param("nickname", "testuser"))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.data.id").value(1L));
  }

  @Test
  @DisplayName("회원가입 API 실패 - 잘못된 이메일 형식")
  void signUpApi_Fail_InvalidEmail() throws Exception {
    // given
    when(authService.signUpUser(any(SignUpRequest.class)))
      .thenThrow(new CustomException(GlobalExceptionCode.INVALID_EMAIL_FORMAT));

    // when & then
    mockMvc.perform(multipart("/api/auth/signup")
        .file(mockFile)
        .param("email", "invalid-email")
        .param("password", "password123!")
        .param("nickname", "testuser"))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("로그인 API 성공 시 쿠키 생성 및 사용자 정보 반환 테스트")
  void loginApi_Success() throws Exception {
    // --- GIVEN (주어진 상황) ---

    // 1. 로그인 요청 객체 생성
    LoginRequest request = new LoginRequest("test@test.com", "password");

    // 2. AuthService가 반환할 가짜 유저 객체 생성
    User user = User.builder()
      .id(1L)
      .email("test@test.com")
      .nickname("testuser")
      .build();

    // 3. AuthService가 반환할 가짜 결과 객체 생성 (토큰과 유저 정보 포함)
    String accessToken = "dummy-access-token";
    String refreshToken = "dummy-refresh-token";
    LoginResultResponse loginResult = new LoginResultResponse(user, accessToken, refreshToken);

    // 4. authService.login()이 호출되면, 위에서 만든 가짜 결과를 반환하도록 설정
    when(authService.login(any(LoginRequest.class), any(), any())).thenReturn(loginResult);

    // --- WHEN (API를 호출했을 때) ---
    mockMvc.perform(post("/api/auth/login") // AuthController에 정의된 경로
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))

      // --- THEN (이러한 결과가 나와야 함) ---
      // 1. HTTP 상태 코드는 200 OK 여야 한다.
      .andExpect(status().isOk())

      // 2. 응답 본문의 JSON 데이터는 기대한 값을 가져야 한다.
      .andExpect(jsonPath("$.code").value("COMMON_SUCCESS"))
      .andExpect(jsonPath("$.data.userId").value(user.getId()))
      .andExpect(jsonPath("$.data.email").value(user.getEmail()))
      .andExpect(jsonPath("$.data.nickname").value(user.getNickname()));

    // 3. CookieUtil의 메서드들이 올바른 토큰 값으로 호출되었는지 검증해야 한다.
    verify(cookieUtil).createAccessTokenCookie(any(HttpServletResponse.class), eq(accessToken));
    verify(cookieUtil).createRefreshTokenCookie(any(HttpServletResponse.class), eq(refreshToken));
  }

  @Test
  @DisplayName("랜덤 닉네임 생성 API 성공 테스트")
  void getRandomNickname_Success() throws Exception {
    // given
    String expectedNickname = "형용사 단어 12345";
    when(userService.generateRandomNickname()).thenReturn(expectedNickname);

    // when & then
    mockMvc.perform(get("/api/auth/random-nickname"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("COMMON_SUCCESS"))
      .andExpect(jsonPath("$.data").value(expectedNickname));
      
    verify(userService).generateRandomNickname();
  }
}
