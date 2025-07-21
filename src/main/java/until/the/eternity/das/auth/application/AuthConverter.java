package until.the.eternity.das.auth.application;

import org.springframework.stereotype.Component;
import until.the.eternity.das.auth.dto.request.SignUpRequest;
import until.the.eternity.das.auth.dto.response.SignUpResponse;
import until.the.eternity.das.role.entity.Role;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.enums.Status;

@Component
public class AuthConverter {

  public User fromUserSignUpRequestToUser(SignUpRequest request, String passwordHash,
      Role role) {
    return User.builder()
        .email(request.email())
        .passwordHash(passwordHash)
        .nickname(request.nickname())
        .status(Status.ACTIVE)
        .role(role)
        .build();
  }

  public SignUpResponse fromUserToUserSignUpResponse(User user) {
    return SignUpResponse.of(user.getId());
  }

}
