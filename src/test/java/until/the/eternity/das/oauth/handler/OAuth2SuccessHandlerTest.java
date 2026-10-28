package until.the.eternity.das.oauth.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import until.the.eternity.das.common.util.CookieUtil;
import until.the.eternity.das.common.util.JwtUtil;
import until.the.eternity.das.oauth.dto.OauthUserDTO;
import until.the.eternity.das.oauth.entity.OauthUser;
import until.the.eternity.das.oauth.entity.OauthUserRepository;
import until.the.eternity.das.oauth.service.OAuthExchangeService;
import until.the.eternity.das.token.application.TokenService;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;

@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {
    @Mock private UserRepository users;
    @Mock private OauthUserRepository oauthUsers;
    @Mock private JwtUtil jwt;
    @Mock private CookieUtil cookies;
    @Mock private TokenService tokens;
    @Mock private OAuthExchangeService exchanges;
    @InjectMocks private OAuth2SuccessHandler handler;

    @BeforeEach
    void configureRedirect() {
        ReflectionTestUtils.setField(
                handler, "frontendCallbackUrl", "https://www.memonogi.com/social-callback");
    }

    @Test
    void newUserRedirectContainsOpaqueCodeOnly() throws Exception {
        OauthUserDTO identity =
                new OauthUserDTO("provider-secret-id", "private@example.invalid", "GOOGLE");
        when(exchanges.newBinding()).thenReturn("binding-value");
        when(exchanges.issueSignupCode(identity, "binding-value")).thenReturn("opaque-code");
        MockHttpServletResponse response = redirect(identity);

        assertEquals(
                "https://www.memonogi.com/social-callback?code=opaque-code",
                response.getRedirectedUrl());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
        assertFalse(response.getRedirectedUrl().contains("private@example.invalid"));
        assertFalse(response.getRedirectedUrl().contains("provider-secret-id"));
        verify(cookies).createOAuthBindingCookie(response, "binding-value");
    }

    @Test
    void existingUserRedirectDoesNotContainAccountData() throws Exception {
        OauthUserDTO identity =
                new OauthUserDTO("provider-secret-id", "private@example.invalid", "GOOGLE");
        User user =
                User.builder()
                        .id(42L)
                        .email("private@example.invalid")
                        .nickname("private-nickname")
                        .build();
        OauthUser oauthUser = org.mockito.Mockito.mock(OauthUser.class);
        when(oauthUser.getUser()).thenReturn(user);
        when(oauthUsers.findByProviderAndProviderUserIdWithUserAndRoles(
                        "GOOGLE", "provider-secret-id"))
                .thenReturn(Optional.of(oauthUser));
        when(exchanges.newBinding()).thenReturn("binding-value");
        when(exchanges.issueLoginCode("binding-value")).thenReturn("opaque-code");
        when(jwt.generateAccessToken(user)).thenReturn("access-token");
        when(jwt.generateRefreshToken(user)).thenReturn("refresh-token");
        MockHttpServletResponse response = redirect(identity);

        assertEquals(
                "https://www.memonogi.com/social-callback?code=opaque-code",
                response.getRedirectedUrl());
        assertFalse(response.getRedirectedUrl().contains("private@example.invalid"));
        assertFalse(response.getRedirectedUrl().contains("private-nickname"));
        verify(cookies).createAccessTokenCookie(response, "access-token");
        verify(cookies).createRefreshTokenCookie(response, "refresh-token");
    }

    @Test
    void duplicateEmailRedirectHasOnlyGenericErrorCode() throws Exception {
        OauthUserDTO identity =
                new OauthUserDTO("provider-secret-id", "private@example.invalid", "GOOGLE");
        when(users.findByEmail("private@example.invalid"))
                .thenReturn(
                        Optional.of(
                                User.builder().id(42L).email("private@example.invalid").build()));
        MockHttpServletResponse response = redirect(identity);

        assertEquals(
                "https://www.memonogi.com/social-callback?error=oauth_account_exists",
                response.getRedirectedUrl());
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    @Test
    void missingProviderEmailCannotIssueSignupCode() throws Exception {
        OauthUserDTO identity = new OauthUserDTO("provider-secret-id", null, "GOOGLE");
        MockHttpServletResponse response = redirect(identity);

        assertEquals(
                "https://www.memonogi.com/social-callback?error=oauth_identity_unavailable",
                response.getRedirectedUrl());
        verifyNoInteractions(exchanges, cookies);
    }

    @Test
    void sentinelProviderIdCannotIssueCodeOrJwt() throws Exception {
        MockHttpServletResponse response =
                redirect(new OauthUserDTO("null", "private@example.invalid", "KAKAO"));

        assertEquals(
                "https://www.memonogi.com/social-callback?error=oauth_identity_unavailable",
                response.getRedirectedUrl());
        verifyNoInteractions(exchanges, cookies, jwt, tokens, oauthUsers);
    }

    @Test
    void failedRefreshTokenWriteDoesNotIssueCodeOrCookies() {
        OauthUserDTO identity =
                new OauthUserDTO("provider-secret-id", "private@example.invalid", "GOOGLE");
        User user = User.builder().id(42L).email("private@example.invalid").build();
        OauthUser oauthUser = org.mockito.Mockito.mock(OauthUser.class);
        when(oauthUser.getUser()).thenReturn(user);
        when(oauthUsers.findByProviderAndProviderUserIdWithUserAndRoles(
                        "GOOGLE", "provider-secret-id"))
                .thenReturn(Optional.of(oauthUser));
        when(jwt.generateAccessToken(user)).thenReturn("access-token");
        when(jwt.generateRefreshToken(user)).thenReturn("refresh-token");
        doThrow(new IllegalStateException("synthetic storage failure"))
                .when(tokens)
                .saveNewRefreshToken(42L, "refresh-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(
                IllegalStateException.class,
                () ->
                        handler.onAuthenticationSuccess(
                                new MockHttpServletRequest(),
                                response,
                                new UsernamePasswordAuthenticationToken(identity, null)));
        assertEquals(null, response.getRedirectedUrl());
        verifyNoInteractions(exchanges, cookies);
    }

    private MockHttpServletResponse redirect(OauthUserDTO identity) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                new UsernamePasswordAuthenticationToken(identity, null));
        return response;
    }
}
