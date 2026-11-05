package until.the.eternity.das.user.dto.response;

import java.util.ArrayList;
import java.util.List;
import until.the.eternity.das.user.entity.UserGameProfile;

final class ProfileResponseValues {
    private ProfileResponseValues() {}

    static List<String> preferredColors(UserGameProfile profile) {
        if (profile == null) {
            return List.of();
        }
        List<String> colors = new ArrayList<>(2);
        if (profile.getPreferredColor1() != null) {
            colors.add(profile.getPreferredColor1());
        }
        if (profile.getPreferredColor2() != null) {
            colors.add(profile.getPreferredColor2());
        }
        return List.copyOf(colors);
    }
}
