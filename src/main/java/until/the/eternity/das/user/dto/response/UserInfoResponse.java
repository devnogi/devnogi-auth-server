package until.the.eternity.das.user.dto.response;

import until.the.eternity.das.user.entity.User;

import java.time.LocalDateTime;

public record UserInfoResponse(
  Long userId,
  String email,
  String nickname,
  String profileImageUrl,
  String role,
  boolean verified,
  LocalDateTime createdAt,
  LocalDateTime lastLoginAt
) {
  public static UserInfoResponse of(User user) {
    return new UserInfoResponse(
      user.getId(),
      user.getEmail(),
      user.getNickname(),
      user.getProfileImageUrl(),
      user.getRole()
        .getName()
        .name(),
      user.isVerified(),
      user.getCreatedAt(),
      user.getLastLoginAt()
    );
  }
}
