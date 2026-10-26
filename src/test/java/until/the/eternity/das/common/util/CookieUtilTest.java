package until.the.eternity.das.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import until.the.eternity.das.common.constant.JwtConstant;

import static org.assertj.core.api.Assertions.assertThat;

class CookieUtilTest {
  private JwtConstant settings() {
    JwtConstant settings = new JwtConstant();
    settings.setAccessTokenCookieName("access_token");
    settings.setRefreshTokenCookieName("refresh_token");
    settings.setAccessTokenValidity(3600000L);
    settings.setRefreshTokenValidity(86400000L);
    return settings;
  }

  @Test
  void productionDefaultsAndDeletionUseSameScope() {
    CookieUtil cookies = new CookieUtil(settings());
    MockHttpServletResponse response = issueAndDelete(cookies);
    assertThat(response.getHeaders("Set-Cookie")).hasSize(4).allSatisfy(cookie ->
      assertThat(cookie).contains("Domain=.memonogi.com", "Secure", "HttpOnly", "Path=/", "SameSite=None"));
    assertLifetimes(response);
  }

  @Test
  void explicitLocalSettingsCreateHostOnlyHttpCookiesAndMatchingDeletion() {
    JwtConstant settings = settings();
    settings.setCookieDomain("");
    settings.setCookieSecure(false);
    settings.setCookieSameSite("Lax");
    MockHttpServletResponse response = issueAndDelete(new CookieUtil(settings));
    assertThat(response.getHeaders("Set-Cookie")).hasSize(4).allSatisfy(cookie ->
      assertThat(cookie).contains("HttpOnly", "Path=/", "SameSite=Lax").doesNotContain("Domain=", "Secure"));
    assertLifetimes(response);
  }

  @Test
  void oauthBindingCookieUsesSameBrowserScopeAndShortLifetime() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    new CookieUtil(settings()).createOAuthBindingCookie(response, "synthetic-binding");
    assertThat(response.getHeader("Set-Cookie"))
      .startsWith("oauth_flow_binding=synthetic-binding")
      .contains("Max-Age=600", "Domain=.memonogi.com", "Secure", "HttpOnly", "SameSite=None");
  }

  private MockHttpServletResponse issueAndDelete(CookieUtil cookies) {
    MockHttpServletResponse response = new MockHttpServletResponse();
    cookies.createAccessTokenCookie(response, "synthetic-access");
    cookies.createRefreshTokenCookie(response, "synthetic-refresh");
    cookies.deleteCookie("access_token", response);
    cookies.deleteCookie("refresh_token", response);
    return response;
  }

  private void assertLifetimes(MockHttpServletResponse response) {
    var headers = response.getHeaders("Set-Cookie").stream().toList();
    assertThat(headers.get(0)).startsWith("access_token=").contains("Max-Age=3600");
    assertThat(headers.get(1)).startsWith("refresh_token=").contains("Max-Age=86400");
    assertThat(headers.get(2)).startsWith("access_token=;").contains("Max-Age=0");
    assertThat(headers.get(3)).startsWith("refresh_token=;").contains("Max-Age=0");
  }
}
