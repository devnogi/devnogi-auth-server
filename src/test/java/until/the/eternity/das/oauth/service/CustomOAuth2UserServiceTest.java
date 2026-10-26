package until.the.eternity.das.oauth.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomOAuth2UserServiceTest {

  @Test
  void kakaoIdMustBePresentPositiveInteger() {
    assertEquals("123", CustomOAuth2UserService.validatedProviderId("kakao", 123L));
    assertEquals("123", CustomOAuth2UserService.validatedProviderId("kakao", 123));
    for (Object invalid : new Object[] {null, "null", "123", "", 0L, -1L, 1.5}) {
      assertThrows(OAuth2AuthenticationException.class,
        () -> CustomOAuth2UserService.validatedProviderId("kakao", invalid));
    }
  }

  @Test
  void stringProviderIdsCannotBeMissingOrSentinels() {
    for (String provider : new String[] {"google", "naver"}) {
      assertEquals("provider-id", CustomOAuth2UserService.validatedProviderId(provider, "provider-id"));
      for (Object invalid : new Object[] {null, "", "   ", "null", " NULL ", 123L}) {
        assertThrows(OAuth2AuthenticationException.class,
          () -> CustomOAuth2UserService.validatedProviderId(provider, invalid));
      }
    }
  }
}
