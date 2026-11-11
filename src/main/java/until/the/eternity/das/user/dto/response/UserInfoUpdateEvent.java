package until.the.eternity.das.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import until.the.eternity.das.experience.ActivityGrade;
import until.the.eternity.das.user.entity.User;

public record UserInfoUpdateEvent(
        Long id,
        String nickname,
        String profileImageUrl,
        String serverName,
        boolean verified,
        Long revision,
        ActivityGrade activityGrade,
        @JsonInclude(JsonInclude.Include.ALWAYS) String arcana) {
    public UserInfoUpdateEvent(
            Long id,
            String nickname,
            String profileImageUrl,
            String serverName,
            boolean verified,
            Long revision,
            ActivityGrade activityGrade) {
        this(id, nickname, profileImageUrl, serverName, verified, revision, activityGrade, null);
    }

    public UserInfoUpdateEvent(
            Long id,
            String nickname,
            String profileImageUrl,
            String serverName,
            boolean verified,
            Long revision) {
        this(id, nickname, profileImageUrl, serverName, verified, revision, null);
    }

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
                revision,
                user.visibleActivityGrade());
    }
}
