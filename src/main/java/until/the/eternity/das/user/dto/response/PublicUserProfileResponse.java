package until.the.eternity.das.user.dto.response;

import java.util.List;
import until.the.eternity.das.experience.ActivityGrade;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserGameProfile;

public record PublicUserProfileResponse(
        Long userId,
        String nickname,
        String profileImageUrl,
        String serverName,
        boolean verified,
        int verificationCount,
        Integer mainChannel,
        String guildName,
        String race,
        String arcana,
        List<String> preferredColors,
        String mbti,
        String bio,
        ActivityGrade activityGrade) {
    public static PublicUserProfileResponse of(
            User user, UserGameProfile profile, int verificationCount) {
        return new PublicUserProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getServerName(),
                user.isVerified(),
                verificationCount,
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
