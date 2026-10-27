package until.the.eternity.das.oauth.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import until.the.eternity.das.common.util.CookieUtil;
import until.the.eternity.das.common.util.JwtUtil;
import until.the.eternity.das.oauth.dto.OauthUserDTO;
import until.the.eternity.das.oauth.entity.OauthUser;
import until.the.eternity.das.oauth.entity.OauthUserRepository;
import until.the.eternity.das.oauth.service.OAuthExchangeService;
import until.the.eternity.das.token.application.TokenService;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

  private final UserRepository userRepository;
  private final OauthUserRepository oauthUserRepository;
  private final JwtUtil jwtUtil;
  private final CookieUtil cookieUtil;
  private final TokenService tokenService;
  private final OAuthExchangeService exchangeService;

  @Value("${app.oauth.redirect-uri:https://www.memonogi.com/social-callback}")
  private String frontendCallbackUrl;

  @Override
  public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                      Authentication authentication) throws IOException {

    OauthUserDTO oauthUserDTO = getOauthUserDTO(authentication);

    if (oauthUserDTO.getProvider() == null || oauthUserDTO.getProvider().isBlank()
      || oauthUserDTO.getProviderUserId() == null || oauthUserDTO.getProviderUserId().isBlank()
      || "null".equalsIgnoreCase(oauthUserDTO.getProviderUserId().trim())) {
      redirectToFrontendWithError(response, "oauth_identity_unavailable");
      return;
    }

    Optional<OauthUser> oauthUserOpt = oauthUserRepository.findByProviderAndProviderUserIdWithUserAndRoles(
      oauthUserDTO.getProvider(), oauthUserDTO.getProviderUserId());

    if (oauthUserOpt.isPresent()) {
      User user = oauthUserOpt.get()
        .getUser();
      redirectWithSuccess(response, user);
      return;
    }

    if (oauthUserDTO.getEmail() != null) {
      Optional<User> userOpt = userRepository.findByEmail(oauthUserDTO.getEmail());
      if (userOpt.isPresent()) {
        redirectToFrontendWithError(response, "oauth_account_exists");
        return;
      }
    }

    if (oauthUserDTO.getEmail() == null || oauthUserDTO.getEmail().isBlank()) {
      redirectToFrontendWithError(response, "oauth_identity_unavailable");
      return;
    }

    String binding = exchangeService.newBinding();
    String code = exchangeService.issueSignupCode(oauthUserDTO, binding);
    cookieUtil.createOAuthBindingCookie(response, binding);
    redirectToFrontend(response, code);
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

  /**
   * 로그인 성공 시 JWT 토큰 발급 후 프론트엔드로 리다이렉트
   */
  private void redirectWithSuccess(HttpServletResponse response, User user) throws IOException {
    String accessToken = jwtUtil.generateAccessToken(user);
    String refreshToken = jwtUtil.generateRefreshToken(user);

    tokenService.saveNewRefreshToken(user.getId(), refreshToken);

    user.updateLastLoginAt();
    userRepository.save(user);

    String binding = exchangeService.newBinding();
    String code = exchangeService.issueLoginCode(binding);
    cookieUtil.createAccessTokenCookie(response, accessToken);
    cookieUtil.createRefreshTokenCookie(response, refreshToken);
    cookieUtil.createOAuthBindingCookie(response, binding);
    redirectToFrontend(response, code);
  }

  /**
   * 프론트엔드 콜백 URL로 리다이렉트
   */
  private void redirectToFrontend(HttpServletResponse response, String code) throws IOException {
    noStore(response);
    response.sendRedirect(frontendCallbackUrl + "?code=" + code);
  }

  /**
   * 에러 발생 시 프론트엔드로 리다이렉트
   */
  private void redirectToFrontendWithError(HttpServletResponse response, String errorCode) throws IOException {
    noStore(response);
    response.sendRedirect(frontendCallbackUrl + "?error=" + errorCode);
  }

  private void noStore(HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Referrer-Policy", "no-referrer");
  }
}
