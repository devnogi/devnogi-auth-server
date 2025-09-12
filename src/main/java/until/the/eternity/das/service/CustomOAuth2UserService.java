package until.the.eternity.das.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import until.the.eternity.das.oauth.entity.OauthUser;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.oauth.GoogleOAuth2UserInfo;
import until.the.eternity.das.oauth.KakaoOAuth2UserInfo;
import until.the.eternity.das.oauth.NaverOAuth2UserInfo;
import until.the.eternity.das.oauth.OAuth2UserInfo;
import until.the.eternity.das.repository.OAuthUserRepository;
import until.the.eternity.das.repository.UserRepository;

import java.util.Collections;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final OAuthUserRepository oAuthUserRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomOAuth2UserService(UserRepository userRepository, OAuthUserRepository oAuthUserRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.oAuthUserRepository = oAuthUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfo oAuth2UserInfo = getOAuth2UserInfo(provider, oAuth2User.getAttributes());

        Optional<OauthUser> oAuthUserOptional = oAuthUserRepository.findByProviderAndProviderUserId(
                oAuth2UserInfo.getProvider(), oAuth2UserInfo.getProviderId());

        User user;
        if (oAuthUserOptional.isPresent()) {
            // 이미 연동된 계정이 있는 경우
            user = oAuthUserOptional.get().getUser();
        } else {
            // 신규 연동인 경우
            Optional<User> userOptional = userRepository.findByEmail(oAuth2UserInfo.getEmail());
            if (userOptional.isPresent()) {
                // 이미 같은 이메일의 로컬 계정이 있는 경우 -> 연동
                user = userOptional.get();
            } else {
                // 완전 신규 가입
                user = registerNewUser(oAuth2UserInfo);
            }
            // OAuth 정보 저장
            registerOAuthUser(user, oAuth2UserInfo);
        }

        return new org.springframework.security.oauth2.core.user.DefaultOAuth2User(
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().getName().name())),
                oAuth2User.getAttributes(),
                "email"); // nameAttributeKey
    }

    private OAuth2UserInfo getOAuth2UserInfo(String provider, Map<String, Object> attributes) {
        if (provider.equalsIgnoreCase("google")) {
            return new GoogleOAuth2UserInfo(attributes);
        } else if (provider.equalsIgnoreCase("naver")) {
            return new NaverOAuth2UserInfo(attributes);
        } else if (provider.equalsIgnoreCase("kakao")) {
            return new KakaoOAuth2UserInfo(attributes);
        } else {
            throw new OAuth2AuthenticationException("Unsupported Provider: " + provider);
        }
    }

    private User registerNewUser(OAuth2UserInfo oAuth2UserInfo) {
        User newUser = User.builder()
                .email(oAuth2UserInfo.getEmail())
                .nickname(oAuth2UserInfo.getName() + "_" + oAuth2UserInfo.getProvider())
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString())) // 사용하지 않을 비밀번호
                // .roles(Set.of(roleRepository.findByName("ROLE_USER").get())) // 기본 역할 부여
                .build();
        return userRepository.save(newUser);
    }

    private void registerOAuthUser(User user, OAuth2UserInfo oAuth2UserInfo) {
        OauthUser oAuthUser = OauthUser.builder()
                .user(user)
                .provider(oAuth2UserInfo.getProvider())
                .providerUserId(oAuth2UserInfo.getProviderId())
                .build();
        oAuthUserRepository.save(oAuthUser);
    }
}
