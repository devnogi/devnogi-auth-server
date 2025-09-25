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
    ResponseCookie cookie = ResponseCookie.from(jwtConstant.getACCESS_TOKEN_COOKIE_NAME(), accessToken)
      .httpOnly(true)
      .secure(true)
      .sameSite("None")
      .path("/")
      .maxAge(jwtConstant.getAccessTokenValidity())
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
    ResponseCookie cookie = ResponseCookie.from(jwtConstant.getREFRESH_TOKEN_COOKIE_NAME(), refreshToken)
      .httpOnly(true)
      .secure(true)
      .sameSite("None")
      .path("/")
      .maxAge(jwtConstant.getRefreshTokenValidity())
      .build();

    response.addHeader("Set-Cookie", cookie.toString());
  }

  /**
   * 토큰 쿠키들 삭제(로그아웃 시 사용) -> 그런데 토큰을 삭제하는게 아니라 새로운 토큰을 추가하는게 맞아..?
   *
   * @param response
   */
  public void deleteCookie(String name, HttpServletResponse response) {
    ResponseCookie cookie = ResponseCookie.from(name, "")
      .httpOnly(true)
      .secure(true)
      .sameSite("None")
      .path("/")
      .maxAge(0)
      .build();

    response.addHeader("Set-Cookie", cookie.toString());
  }
}
