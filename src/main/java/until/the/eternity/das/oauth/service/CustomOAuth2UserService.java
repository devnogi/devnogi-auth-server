package until.the.eternity.das.oauth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.common.util.MapUtil;
import until.the.eternity.das.oauth.dto.OauthUserDTO;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

  @Override
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User oauth2User = super.loadUser(userRequest);

    String provider = userRequest.getClientRegistration()
      .getRegistrationId(); // google, kakao, naver
    Map<String, Object> attributes = oauth2User.getAttributes();

    String providerUserId;
    String email;

    switch (provider) {
      case "google":
        providerUserId = (String) attributes.get("sub");
        email = (String) attributes.get("email");
        break;
      case "naver":
        Object responseObj = attributes.get("response");
        if (!(responseObj instanceof Map)) {
          log.error("Expected 'response' attribute to be a Map for provider 'naver', but got: {}",
            responseObj == null ? "null" : responseObj.getClass()
              .getName());
          throw new CustomException(GlobalExceptionCode.NOT_SUPPORTED_PROVIDER);
        }
        Map<String, Object> response = MapUtil.cast(responseObj, Map.class);
        providerUserId = (String) response.get("id");
        email = (String) response.get("email");
        break;
      case "kakao":
        providerUserId = String.valueOf(attributes.get("id"));
        Object kakaoAccountObj = attributes.get("kakao_account");
        if (!(kakaoAccountObj instanceof Map)) {
          log.error("Expected 'kakao_account' attribute to be a Map for provider 'kakao', but got: {}",
            kakaoAccountObj == null ? "null" : kakaoAccountObj.getClass()
              .getName());
          throw new CustomException(GlobalExceptionCode.NOT_SUPPORTED_PROVIDER);
        }
        Map<String, Object> kakaoAccount = MapUtil.cast(kakaoAccountObj, Map.class);
        email = (String) kakaoAccount.get("email");
        break;
      default:
        throw new CustomException(GlobalExceptionCode.NOT_SUPPORTED_PROVIDER);
    }

    log.debug("소셜 로그인 성공 - provider={}, providerUserId={}, email={}", provider, providerUserId, email);

    return new OauthUserDTO(providerUserId, email, provider.toUpperCase());
  }
}