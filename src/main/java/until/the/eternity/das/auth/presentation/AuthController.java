package until.the.eternity.das.auth.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import until.the.eternity.das.auth.application.AuthService;
import until.the.eternity.das.auth.dto.request.LoginRequest;
import until.the.eternity.das.auth.dto.request.SignUpRequest;
import until.the.eternity.das.auth.dto.request.SocialSignUpRequest;
import until.the.eternity.das.auth.dto.response.LoginResponse;
import until.the.eternity.das.auth.dto.response.LoginResultResponse;
import until.the.eternity.das.auth.dto.response.SignUpResponse;
import until.the.eternity.das.common.constant.JwtConstant;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.common.response.CommonResponse;
import until.the.eternity.das.common.util.CookieUtil;
import until.the.eternity.das.common.util.JwtUtil;
import until.the.eternity.das.oauth.service.SocialAuthService;
import until.the.eternity.das.token.application.TokenService;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final TokenService tokenService;
  private final SocialAuthService socialAuthService;
  private final CookieUtil cookieUtil;
  private final JwtUtil jwtUtil;
  private final JwtConstant jwtConstant;

  /**
   * 회원 가입 API
   *
   * @param request 회원 가입 요청 정보 (이메일, 비밀번호, 닉네임)
   * @return 생성된 회원 정보가 포함된 ResponseEntity 객체 ID (HTTP 상태 201 Created)
   */
  @PostMapping("/signup")
  @Operation(summary = "회원 가입 API", description = """
    - Description : 이 API는 회원 가입을 요청합니다.
    - Assignee : 안나
    """)
  @ApiResponse(
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = SignUpResponse.class)))
  public ResponseEntity<CommonResponse<SignUpResponse>> signUp(@ModelAttribute SignUpRequest request) {
    return ResponseEntity.status(CREATED)
      .body(CommonResponse.success(authService.signUpUser(request)));
  }

  //Todo : SuperAdmin만 접근 가능하도록 권한 설정 필요

  /**
   * 회원 가입 API
   *
   * @param request 관리자 가입 요청 정보 (이메일, 비밀번호, 닉네임)
   * @return 생성된 관리자  정보가 포함된 ResponseEntity 객체 ID (HTTP 상태 201 Created)
   */
  @PostMapping("/admin/signup")
  @Operation(summary = "관리자 가입 API", description = """
    - Description : 이 API는 관리자 가입을 요청합니다.
    - Assignee : 안나
    """)
  @ApiResponse(
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = SignUpResponse.class)))
  public ResponseEntity<CommonResponse<SignUpResponse>> signUpAdmin(@ModelAttribute SignUpRequest request) {
    return ResponseEntity.status(CREATED)
      .body(CommonResponse.success(authService.signUpAdmin(request)));
  }


  /**
   * 이메일 중복 체크 API
   *
   * @param email 체크할 이메일 (쿼리 파라미터)
   * @return 이메일 사용 가능 여부
   */
  @GetMapping("/check-email")
  @Operation(summary = "이메일 중복 확인 API", description = """
    - Description : 입력한 이메일이 이미 가입되어 있는지 확인합니다.
    - 사용 가능한 이메일이면 true, 중복된 이메일이면 false를 반환합니다.
    - Assignee : 안나
    """)
  public ResponseEntity<CommonResponse<Boolean>> checkEmail(
    @RequestParam(name = "email") @NotBlank String email) {

    authService.isValidEmailFormat(email);

    boolean exists = authService.existsByEmail(email);
    return ResponseEntity.ok(CommonResponse.success(!exists));
  }

  /**
   * 닉네임 중복 체크 API
   *
   * @param nickname 체크할 닉네임 (쿼리 파라미터)
   * @return 닉네임 사용 가능 여부
   */
  @GetMapping("/check-nickname")
  @Operation(summary = "닉네임 중복 확인 API", description = """
    - Description : 입력한 닉네임이 이미 사용 중인지 확인합니다.
    - 사용 가능한 닉네임이면 true, 중복된 닉네임이면 false를 반환합니다.
    - Assignee : 안나
    """)
  public ResponseEntity<CommonResponse<Boolean>> checkNickname(
    @RequestParam(name = "nickname") @NotBlank String nickname) {

    authService.isValidNicknameFormat(nickname);

    boolean exists = authService.existsByNickname(nickname);
    return ResponseEntity.ok(CommonResponse.success(!exists));
  }

  /**
   * 소셜 회원가입 API
   *
   * @param request 소셜 회원가입 요청정보(이메일, 닉네임)
   * @return 생성된 사용자의 ID값
   */
  @PostMapping("/signup/social")
  @Operation(summary = "소셜 회원가입 API", description = """
    - Description : 이 API는 소셜 회원가입을 요청합니다
    - Assignee : 장욱
    """)
  @ApiResponse(
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = SignUpResponse.class)))
  public ResponseEntity<CommonResponse<SignUpResponse>> completeSocialSignup(
    @ModelAttribute SocialSignUpRequest request,
    HttpServletResponse response
  ) {
    SignUpResponse result = socialAuthService.completeSocialSignup(request);

    LoginResultResponse loginResultResponse = socialAuthService.jwtForSocialSignUp(result.id());

    cookieUtil.createAccessTokenCookie(response, loginResultResponse.accessToken());
    cookieUtil.createRefreshTokenCookie(response, loginResultResponse.refreshToken());

    return ResponseEntity.status(CREATED)
      .body(CommonResponse.success(result));
  }

  /**
   * 로그아웃 API
   *
   * @param response 쿠키 삭제를 위한 HttpResponse
   * @return 성공 응답
   */
  @PostMapping("/logout")
  @Operation(summary = "로그아웃 API", description = """
    - Description : 이 API는 로그아웃을 요청합니다. Refresh Token을 revoke하고 쿠키를 삭제합니다.
    - Assignee : 안나
    """)
  public ResponseEntity<CommonResponse<Void>> logout(HttpServletResponse response) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof Long userId) {
      authService.logout(userId);
    }

    cookieUtil.deleteCookie(jwtConstant.getAccessTokenCookieName(), response);
    cookieUtil.deleteCookie(jwtConstant.getRefreshTokenCookieName(), response);

    return ResponseEntity.ok(CommonResponse.success(null));
  }

  /**
   * 이메일 로그인 API
   *
   * @param request 로그인 요청정보(이메일, 비밀번호)
   * @param response 발급된 JWT를 Cookie에 담기 위한 HttpResponse
   * @return 로그인 된 유저의 정보
   */
  @PostMapping("/login")
  @Operation(summary = "이메일 로그인 API", description = """
    - Description : 이 API는 로그인을 요청합니다
    - Assignee : 장욱
    """)
  @ApiResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = LoginResponse.class)))
  public ResponseEntity<CommonResponse<LoginResponse>> login(
    @RequestBody LoginRequest request,
    HttpServletResponse response,
    HttpServletRequest httpServletRequest
  ) {
    String clientIp = httpServletRequest.getRemoteAddr();
    String userAgent = httpServletRequest.getHeader("User-Agent");
    
    LoginResultResponse loginResultResponse = authService.login(request, clientIp, userAgent);

    cookieUtil.createAccessTokenCookie(response, loginResultResponse.accessToken());
    cookieUtil.createRefreshTokenCookie(response, loginResultResponse.refreshToken());

    LoginResponse loginResponse = LoginResponse.from(loginResultResponse.user());

    return ResponseEntity.ok(CommonResponse.success(loginResponse));
  }

  /**
   * 토큰 갱신 API
   *
   * @param request 쿠키에서 refresh token을 추출하기 위한 HttpRequest
   * @param response 새 토큰을 쿠키에 담기 위한 HttpResponse
   * @return 갱신된 사용자 정보
   */
  @PostMapping("/refresh")
  @Operation(summary = "토큰 갱신 API", description = """
    - Description : 리프레시 토큰을 사용하여 새로운 액세스 토큰과 리프레시 토큰을 발급합니다.
    - 기존 리프레시 토큰은 revoke되고 새로운 토큰이 발급됩니다 (토큰 로테이션).
    """)
  @ApiResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = LoginResponse.class)))
  public ResponseEntity<CommonResponse<LoginResponse>> refresh(
    HttpServletRequest request,
    HttpServletResponse response
  ) {
    String refreshToken = jwtUtil.extractRefreshToken(request);
    if (refreshToken == null) {
      throw new CustomException(GlobalExceptionCode.INVALID_REFRESH_TOKEN);
    }

    LoginResultResponse loginResultResponse = tokenService.refresh(refreshToken);

    cookieUtil.createAccessTokenCookie(response, loginResultResponse.accessToken());
    cookieUtil.createRefreshTokenCookie(response, loginResultResponse.refreshToken());

    LoginResponse loginResponse = LoginResponse.from(loginResultResponse.user());

    return ResponseEntity.ok(CommonResponse.success(loginResponse));
  }
}
