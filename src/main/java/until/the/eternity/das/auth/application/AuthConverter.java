package until.the.eternity.das.auth.application;

import org.springframework.stereotype.Component;
import until.the.eternity.das.auth.dto.request.SignUpRequest;
import until.the.eternity.das.auth.dto.response.SignUpResponse;
import until.the.eternity.das.oauth.dto.OauthUserDTO;
import until.the.eternity.das.role.entity.Role;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.enums.Status;

@Component
public class AuthConverter {

  // 이메일 회원가입 관련 로직

  public User fromUserSignUpRequestToUser(SignUpRequest request, String passwordHash,
                                          Role role, String profileImageUrl) {
    return User.builder()
      .email(request.email())
      .passwordHash(passwordHash)
      .nickname(request.nickname())
      .status(Status.ACTIVE)
      .profileImageUrl(profileImageUrl)
      .role(role)
      .build();
  }

  public SignUpResponse fromUserToUserSignUpResponse(User user) {
    return SignUpResponse.of(user.getId());
  }

  // 소셜 로그인 회원 저장 관련
  public User fromOauthUserDTOToUser(OauthUserDTO oauthUserDTO, String nickname, Role role, String profileImageUrl) {
    return User.builder()
      .email(oauthUserDTO.getEmail())
      .nickname(nickname)
      .profileImageUrl(profileImageUrl)
      .status(Status.ACTIVE)
      .role(role)
      .build();
  }

}
