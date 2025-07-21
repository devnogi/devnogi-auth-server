package until.the.eternity.das.auth.application;

import org.springframework.stereotype.Component;
import until.the.eternity.das.auth.dto.request.SignUpRequest;
import until.the.eternity.das.auth.dto.response.SignUpResponse;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.enums.Status;

@Component
public class AuthConverter {

  public User fromUserSignUpRequestToUser(SignUpRequest request, String passwordHash) {
    return User.builder()
        .email(request.email())
        .passwordHash(passwordHash)
        .nickname(request.nickname())
        .status(Status.ACTIVE)
        // TODO :  user - role 관계 재정의 및 회원가입시 권한 부여 방법 정의 후 수정 필요
//        .roles(Set.of(Role.builder().name(Name.USER).build())) // 기본 USER 권한 부여
        .build();
  }

  public SignUpResponse fromUserToUserSignUpResponse(User user) {
    return SignUpResponse.of(user.getId());
  }

}
