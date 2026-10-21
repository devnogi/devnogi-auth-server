package until.the.eternity.das.common.util;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import until.the.eternity.das.common.constant.JwtConstant;

@Component
@RequiredArgsConstructor
public class CookieUtil {

  private final JwtConstant jwtConstant;

  /**
   * 액세스 토큰을 HttpOnly 쿠키로 생성
   *
   * @param response
   * @param accessToken
   */
  public void createAccessTokenCookie(HttpServletResponse response, String accessToken) {
    ResponseCookie cookie = cookieBuilder(jwtConstant.getAccessTokenCookieName(), accessToken)
      .maxAge(jwtConstant.getAccessTokenValidity() / 1000)
      .build();

    response.addHeader("Set-Cookie", cookie.toString());
  }

  /**
   * 리프레쉬 토큰을 HttpOnly 쿠키로 생성
   *
   * @param response
   * @param refreshToken
   */
  public void createRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
    ResponseCookie cookie = cookieBuilder(jwtConstant.getRefreshTokenCookieName(), refreshToken)
      .maxAge(jwtConstant.getRefreshTokenValidity() / 1000)
      .build();

    response.addHeader("Set-Cookie", cookie.toString());
  }

  /**
   * 발급과 같은 범위의 만료 쿠키를 보내 브라우저 쿠키를 삭제한다.
   *
   * @param response
   */
  public void deleteCookie(String name, HttpServletResponse response) {
    ResponseCookie cookie = cookieBuilder(name, "")
      .maxAge(0)
      .build();

    response.addHeader("Set-Cookie", cookie.toString());
  }

  private ResponseCookie.ResponseCookieBuilder cookieBuilder(String name, String value) {
    ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
      .httpOnly(true)
      .secure(jwtConstant.isCookieSecure())
      .sameSite(jwtConstant.getCookieSameSite())
      .path("/");
    String domain = jwtConstant.getCookieDomain();
    if (domain != null && !domain.isBlank()) {
      builder.domain(domain);
    }
    return builder;
  }
}
