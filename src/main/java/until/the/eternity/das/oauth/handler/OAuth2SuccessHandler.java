package until.the.eternity.das.oauth.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import until.the.eternity.das.common.response.CommonResponse;
import until.the.eternity.das.common.util.CookieUtil;
import until.the.eternity.das.common.util.JwtUtil;
import until.the.eternity.das.oauth.dto.OauthUserDTO;
import until.the.eternity.das.oauth.entity.OauthUser;
import until.the.eternity.das.oauth.entity.OauthUserRepository;
import until.the.eternity.das.token.application.TokenService;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

  private final UserRepository userRepository;
  private final OauthUserRepository oauthUserRepository;
  private final JwtUtil jwtUtil;
  private final CookieUtil cookieUtil;
  private final ObjectMapper objectMapper;
  private final TokenService tokenService;

  @Override
  public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                      Authentication authentication) throws IOException, ServletException {

    OauthUserDTO oauthUserDTO = getOauthUserDTO(authentication);

    Optional<OauthUser> oauthUserOpt = oauthUserRepository.findByProviderAndProviderUserIdWithUserAndRoles(
      oauthUserDTO.getProvider(), oauthUserDTO.getProviderUserId());

    if (oauthUserOpt.isPresent()) {
      User user = oauthUserOpt.get()
        .getUser();
      issueTokensAndSendSuccessResponse(response, user);
      return;
    }

    if (oauthUserDTO.getEmail() != null) {
      Optional<User> userOpt = userRepository.findByEmail(oauthUserDTO.getEmail());
      if (userOpt.isPresent()) {
        User existingUser = userOpt.get();
        String existingProvider = oauthUserRepository.findByUser(existingUser)
          .map(OauthUser::getProvider)
          .orElse("LOCAL");

        log.warn("이미 {} 계정으로 가입된 이메일({})로 소셜 로그인을 시도했습니다.", existingProvider, oauthUserDTO.getEmail());
        writeJsonResponse(response, CommonResponse.error(
          "DUPLICATE_PROVIDER",
          "이미 " + existingProvider + " 계정으로 가입된 이메일입니다."
        ));
        return;
      }
    }

    log.info("신규 소셜 사용자입니다. 추가 정보 입력이 필요합니다.");

    writeJsonResponse(response, CommonResponse.success(
      "SIGNUP_REQUIRED",
      "소셜 로그인 최초 시도, 추가정보 입력 필요",
      Map.of(
        "provider", oauthUserDTO.getProvider(),
        "providerUserId", oauthUserDTO.getProviderUserId()
      )
    ));
  }

  private OauthUserDTO getOauthUserDTO(Authentication authentication) {
    Object principal = authentication.getPrincipal();

    if (principal instanceof DefaultOidcUser) {
      String provider = ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId()
        .toUpperCase();
      DefaultOidcUser oidcUser = (DefaultOidcUser) principal;
      String providerUserId = oidcUser.getAttribute("sub");
      String email = oidcUser.getAttribute("email");
      return new OauthUserDTO(providerUserId, email, provider);
    } else {
      return (OauthUserDTO) principal;
    }
  }

  private void issueTokensAndSendSuccessResponse(HttpServletResponse response, User user) throws IOException {
    log.info("기존 회원 로그인 성공. JWT 토큰을 발급합니다. User ID: {}", user.getId());
    String accessToken = jwtUtil.generateAccessToken(user);
    String refreshToken = jwtUtil.generateRefreshToken(user);

    tokenService.saveNewRefreshToken(user.getId(), refreshToken);

    cookieUtil.createAccessTokenCookie(response, accessToken);
    cookieUtil.createRefreshTokenCookie(response, refreshToken);

    user.updateLastLoginAt();
    userRepository.save(user);

    writeJsonResponse(response, CommonResponse.success(
      "LOGIN_SUCCESS",
      "로그인 성공",
      Map.of(
        "userId", user.getId(),
        "nickname", user.getNickname(),
        "email", user.getEmail()
      )
    ));
  }

  private void writeJsonResponse(HttpServletResponse response, CommonResponse<?> apiResponse) throws IOException {
    response.setContentType("application/json;charset=UTF-8");
    response.setStatus(HttpServletResponse.SC_OK);
    response.getWriter()
      .write(objectMapper.writeValueAsString(apiResponse));
  }
}