package until.the.eternity.das.user.application;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.user.dto.request.GameProfileInput;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserGameProfile;
import until.the.eternity.das.user.entity.UserGameProfileRepository;
import until.the.eternity.das.user.entity.enums.Arcana;
import until.the.eternity.das.user.entity.enums.Mbti;
import until.the.eternity.das.user.entity.enums.Race;

@Service
@RequiredArgsConstructor
public class GameProfileService {
    private static final Pattern RGB_PATTERN =
            Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final int MAX_GUILD_CODE_POINTS = 30;
    private static final int MAX_BIO_CODE_POINTS = 50;

    private final UserGameProfileRepository profiles;

    public GameProfileValues normalize(GameProfileInput input) {
        Integer mainChannel = normalizeChannel(input.mainChannel());
        String guildName = normalizeText(input.guildName(), MAX_GUILD_CODE_POINTS);
        Race race = parseEnum(input.race(), Race.class);
        Arcana arcana = parseEnum(input.arcana(), Arcana.class);
        String firstColor = normalizeColor(input.preferredColor1());
        String secondColor = normalizeColor(input.preferredColor2());
        Mbti mbti = parseEnum(input.mbti(), Mbti.class);
        String bio = normalizeText(input.bio(), MAX_BIO_CODE_POINTS);

        if (firstColor == null && secondColor != null) {
            firstColor = secondColor;
            secondColor = null;
        }
        return new GameProfileValues(
                mainChannel, guildName, race, arcana, firstColor, secondColor, mbti, bio);
    }

    public void replace(User user, GameProfileValues values) {
        Optional<UserGameProfile> existing = profiles.findById(user.getId());
        if (values.isEmpty()) {
            existing.ifPresent(profiles::delete);
            return;
        }

        UserGameProfile profile =
                existing.orElseGet(() -> UserGameProfile.builder().userId(user.getId()).build());
        profile.replace(values);
        profiles.save(profile);
    }

    public Optional<UserGameProfile> findByUserId(Long userId) {
        return profiles.findById(userId);
    }

    private Integer normalizeChannel(Integer channel) {
        if (channel != null && (channel < 1 || channel > 42)) {
            throw invalidProfile();
        }
        return channel;
    }

    private String normalizeText(String value, int maxCodePoints) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > maxCodePoints
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw invalidProfile();
        }
        return normalized;
    }

    private String normalizeColor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var matcher = RGB_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            throw invalidProfile();
        }
        int red = Integer.parseInt(matcher.group(1));
        int green = Integer.parseInt(matcher.group(2));
        int blue = Integer.parseInt(matcher.group(3));
        if (red > 255 || green > 255 || blue > 255) {
            throw invalidProfile();
        }
        return red + "." + green + "." + blue;
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumType) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalidProfile();
        }
    }

    private CustomException invalidProfile() {
        return new CustomException(GlobalExceptionCode.INVALID_GAME_PROFILE_FORMAT);
    }
}
