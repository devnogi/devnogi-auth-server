package until.the.eternity.das.user.dto.response;

import until.the.eternity.das.user.entity.User;

public record UserInfoUpdateEvent(
        Long id,
        String nickname,
        String profileImageUrl,
        String serverName,
        boolean verified,
        Long revision) {
    public UserInfoUpdateEvent(
            Long id, String nickname, String profileImageUrl, String serverName, boolean verified) {
        this(id, nickname, profileImageUrl, serverName, verified, null);
    }

    public static UserInfoUpdateEvent of(User user) {
        return of(user, null);
    }

    public static UserInfoUpdateEvent of(User user, Long revision) {
        return new UserInfoUpdateEvent(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getServerName(),
                user.isVerified(),
                revision);
    }
}
