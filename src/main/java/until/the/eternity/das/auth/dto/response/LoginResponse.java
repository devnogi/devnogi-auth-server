package until.the.eternity.das.auth.dto.response;

import until.the.eternity.das.user.entity.User;

public record LoginResponse(
  Long userId,
  String nickname,
  String email,
  String profileImageUrl,
  String role
) {
  public static LoginResponse from(User user) {
    return new LoginResponse(
      user.getId(),
      user.getNickname(),
      user.getEmail(),
      user.getProfileImageUrl(),
      user.getRole()
        .getName()
        .name());
  }
}
