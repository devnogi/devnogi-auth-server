package until.the.eternity.das.user.dto.response;

import until.the.eternity.das.user.entity.User;

public record UserInfoUpdateEvent(
  Long id,
  String nickname,
  String profileImageUrl
) {
  public static UserInfoUpdateEvent of(User user) {
    return new UserInfoUpdateEvent(user.getId(), user.getNickname(), user.getProfileImageUrl());
  }
}

// Todo Level, grade(Role)은 추후에 의논 후 추가