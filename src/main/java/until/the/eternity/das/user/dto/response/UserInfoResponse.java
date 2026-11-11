package until.the.eternity.das.user.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import until.the.eternity.das.experience.ActivityGrade;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserGameProfile;

public record UserInfoResponse(
        Long userId,
        String email,
        String nickname,
        String profileImageUrl,
        String serverName,
        String role,
        boolean verified,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt,
        Integer mainChannel,
        String guildName,
        String race,
        String arcana,
        List<String> preferredColors,
        String mbti,
        String bio,
        ActivityGrade activityGrade) {
    public static UserInfoResponse of(User user) {
        return of(user, null);
    }

    public static UserInfoResponse of(User user, UserGameProfile profile) {
        return new UserInfoResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getServerName(),
                user.getRole().getName().name(),
                user.isVerified(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                profile == null ? null : profile.getMainChannel(),
                profile == null ? null : profile.getGuildName(),
                profile == null || profile.getRace() == null ? null : profile.getRace().name(),
                profile == null || profile.getArcana() == null ? null : profile.getArcana().name(),
                ProfileResponseValues.preferredColors(profile),
                profile == null || profile.getMbti() == null ? null : profile.getMbti().name(),
                profile == null ? null : profile.getBio(),
                user.visibleActivityGrade());
    }
}
