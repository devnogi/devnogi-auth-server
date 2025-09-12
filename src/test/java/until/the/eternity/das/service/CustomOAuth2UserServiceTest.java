package until.the.eternity.das.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import until.the.eternity.das.domain.OAuthUser;
import until.the.eternity.das.domain.User;
import until.the.eternity.das.repository.OAuthUserRepository;
import until.the.eternity.das.repository.UserRepository;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @InjectMocks
    private CustomOAuth2UserService customOAuth2UserService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OAuthUserRepository oAuthUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OAuth2UserRequest userRequest;

    private User existingUser;

    @BeforeEach
    void setUp() {
        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("test-id")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("test-redirect-uri")
                .authorizationUri("test-auth-uri")
                .tokenUri("test-token-uri")
                .build();

        given(userRequest.getClientRegistration()).willReturn(clientRegistration);

        existingUser = User.builder()
                .email("test@example.com")
                .nickname("tester")
                .password("encoded-password")
                .build();
    }

    @Test
    @DisplayName("완전 신규 사용자가 소셜 로그인 시 회원가입 및 연동 처리된다")
    void givenNewUser_whenLoadUser_thenRegisterAndLinkUser() {
        // given
        Map<String, Object> attributes = Map.of("sub", "12345", "email", "new@example.com", "name", "newUser");
        OAuth2User oAuth2User = new DefaultOAuth2User(Collections.emptyList(), attributes, "sub");

        given(oAuthUserRepository.findByProviderAndProviderUserId(anyString(), anyString())).willReturn(Optional.empty());
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willReturn(User.builder().build());

        // when
        customOAuth2UserService.loadUser(userRequest);

        // then
        verify(userRepository).save(any(User.class));
        verify(oAuthUserRepository).save(any(OAuthUser.class));
    }

    @Test
    @DisplayName("기존 소셜 연동 사용자가 로그인 시 기존 정보를 반환한다")
    void givenExistingSocialUser_whenLoadUser_thenReturnsExistingUser() {
        // given
        Map<String, Object> attributes = Map.of("sub", "12345", "email", "test@example.com", "name", "tester");
        OAuthUser oAuthUser = OAuthUser.builder().user(existingUser).build();

        given(oAuthUserRepository.findByProviderAndProviderUserId(anyString(), anyString())).willReturn(Optional.of(oAuthUser));

        // when
        customOAuth2UserService.loadUser(userRequest);

        // then
        verify(userRepository, never()).save(any(User.class));
        verify(oAuthUserRepository, never()).save(any(OAuthUser.class));
    }

    @Test
    @DisplayName("기존 로컬 사용자가 소셜 로그인 시 계정이 연동된다")
    void givenExistingLocalUser_whenLoadUser_thenLinksAccount() {
        // given
        Map<String, Object> attributes = Map.of("sub", "12345", "email", "test@example.com", "name", "tester");

        given(oAuthUserRepository.findByProviderAndProviderUserId(anyString(), anyString())).willReturn(Optional.empty());
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(existingUser));

        // when
        customOAuth2UserService.loadUser(userRequest);

        // then
        verify(userRepository, never()).save(any(User.class));
        verify(oAuthUserRepository).save(any(OAuthUser.class));
    }
}