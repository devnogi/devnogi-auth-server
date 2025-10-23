package until.the.eternity.das.user.dto.response;

import until.the.eternity.das.user.entity.User;

import java.time.LocalDateTime;

public record UserInfoResponse(
  String nickname,
  String profileImageUrl,
  LocalDateTime createdAt,
  LocalDateTime lastLoginAt
) {
  public static UserInfoResponse of(User user) {
    return new UserInfoResponse(
      user.getNickname(),
      user.getProfileImageUrl(),
      user.getCreatedAt(),
      user.getLastLoginAt()
    );
  }
}
