package until.the.eternity.das.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.user.dto.request.UserInfoUpdateRequest;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserGameProfile;
import until.the.eternity.das.user.entity.UserGameProfileRepository;
import until.the.eternity.das.user.entity.enums.Arcana;
import until.the.eternity.das.user.entity.enums.Mbti;
import until.the.eternity.das.user.entity.enums.Race;

@ExtendWith(MockitoExtension.class)
class GameProfileServiceTest {
    @Mock private UserGameProfileRepository profiles;
    @InjectMocks private GameProfileService service;

    @Test
    void normalizesAllSupportedValuesAndCompactsSecondColor() {
        GameProfileValues values =
                service.normalize(
                        request(
                                42,
                                "  길드  ",
                                "human",
                                "elemental_knight",
                                null,
                                "001.002.255",
                                "infp",
                                "  소개  "));

        assertEquals(42, values.mainChannel());
        assertEquals("길드", values.guildName());
        assertEquals(Race.HUMAN, values.race());
        assertEquals(Arcana.ELEMENTAL_KNIGHT, values.arcana());
        assertEquals("1.2.255", values.preferredColor1());
        assertNull(values.preferredColor2());
        assertEquals(Mbti.INFP, values.mbti());
        assertEquals("소개", values.bio());
    }

    @Test
    void rejectsOutOfRangeAndControlCharacters() {
        assertInvalid(request(0, null, null, null, null, null, null, null));
        assertInvalid(request(43, null, null, null, null, null, null, null));
        assertInvalid(request(1, null, null, null, "256.0.0", null, null, null));
        assertInvalid(request(1, "길드\n이름", null, null, null, null, null, null));
        assertInvalid(request(1, null, "UNKNOWN", null, null, null, null, null));
        assertInvalid(request(1, "길".repeat(31), null, null, null, null, null, null));
        assertInvalid(request(1, null, null, null, null, null, null, "소".repeat(51)));
    }

    @ParameterizedTest
    @EnumSource(Race.class)
    void acceptsEveryRaceWireCode(Race race) {
        GameProfileValues values =
                service.normalize(request(null, null, race.name(), null, null, null, null, null));

        assertEquals(race, values.race());
    }

    @ParameterizedTest
    @EnumSource(Arcana.class)
    void acceptsEveryArcanaWireCode(Arcana arcana) {
        GameProfileValues values =
                service.normalize(request(null, null, null, arcana.name(), null, null, null, null));

        assertEquals(arcana, values.arcana());
    }

    @ParameterizedTest
    @EnumSource(Mbti.class)
    void acceptsEveryMbtiWireCode(Mbti mbti) {
        GameProfileValues values =
                service.normalize(request(null, null, null, null, null, null, mbti.name(), null));

        assertEquals(mbti, values.mbti());
    }

    @Test
    void createsUpdatesAndDeletesTheOptionalRow() {
        User user = User.builder().id(7L).build();
        GameProfileValues values =
                service.normalize(request(1, null, null, null, "0.0.0", null, null, null));
        when(profiles.findById(7L)).thenReturn(Optional.empty());

        service.replace(user, values);

        ArgumentCaptor<UserGameProfile> saved = ArgumentCaptor.forClass(UserGameProfile.class);
        verify(profiles).save(saved.capture());
        assertEquals("0.0.0", saved.getValue().getPreferredColor1());

        UserGameProfile existing = UserGameProfile.builder().userId(7L).mainChannel(2).build();
        when(profiles.findById(7L)).thenReturn(Optional.of(existing));
        service.replace(
                user, service.normalize(request(null, null, null, null, null, null, null, null)));
        verify(profiles).delete(existing);
        verify(profiles, never()).deleteAll();
    }

    private void assertInvalid(UserInfoUpdateRequest request) {
        CustomException exception =
                assertThrows(CustomException.class, () -> service.normalize(request));
        assertEquals(GlobalExceptionCode.INVALID_GAME_PROFILE_FORMAT, exception.getCode());
    }

    private UserInfoUpdateRequest request(
            Integer channel,
            String guild,
            String race,
            String arcana,
            String color1,
            String color2,
            String mbti,
            String bio) {
        return new UserInfoUpdateRequest(
                null, null, null, channel, guild, race, arcana, color1, color2, mbti, bio);
    }
}
