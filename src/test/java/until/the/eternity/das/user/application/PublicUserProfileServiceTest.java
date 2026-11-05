package until.the.eternity.das.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import until.the.eternity.das.auth.application.NicknameCandidateService;
import until.the.eternity.das.common.application.KafkaProducerService;
import until.the.eternity.das.common.application.S3Service;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.user.dto.response.PublicUserProfileResponse;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserGameProfile;
import until.the.eternity.das.user.entity.UserRepository;
import until.the.eternity.das.user.entity.enums.Arcana;
import until.the.eternity.das.user.entity.enums.Mbti;
import until.the.eternity.das.user.entity.enums.Race;
import until.the.eternity.das.user.entity.enums.Status;
import until.the.eternity.das.user.outbox.UserInfoOutboxService;
import until.the.eternity.das.verification.entity.UserVerification;
import until.the.eternity.das.verification.entity.UserVerificationRepository;

@ExtendWith(MockitoExtension.class)
class PublicUserProfileServiceTest {
    @Mock private UserRepository users;
    @Mock private S3Service images;
    @Mock private KafkaProducerService events;
    @Mock private UserInfoOutboxService outbox;
    @Mock private NicknameCandidateService candidates;
    @Mock private EntityManager entityManager;
    @Mock private GameProfileService gameProfiles;
    @Mock private UserVerificationRepository verifications;
    @InjectMocks private UserService service;

    @Test
    void returnsAnActiveUserWithoutPrivateAccountFields() {
        User user =
                User.builder()
                        .id(7L)
                        .email("private@example.test")
                        .nickname("메모노기")
                        .status(Status.ACTIVE)
                        .verified(true)
                        .serverName("류트")
                        .build();
        when(users.findByIdAndStatus(7L, Status.ACTIVE)).thenReturn(Optional.of(user));
        when(gameProfiles.findByUserId(7L))
                .thenReturn(
                        Optional.of(
                                UserGameProfile.builder()
                                        .userId(7L)
                                        .mainChannel(1)
                                        .guildName("길드")
                                        .race(Race.HUMAN)
                                        .arcana(Arcana.ELEMENTAL_KNIGHT)
                                        .preferredColor1("255.128.0")
                                        .mbti(Mbti.INFP)
                                        .bio("소개")
                                        .build()));
        when(verifications.findByUserId(7L))
                .thenReturn(
                        Optional.of(
                                UserVerification.builder()
                                        .user(user)
                                        .verificationCount(3)
                                        .verified(true)
                                        .build()));

        PublicUserProfileResponse response = service.getPublicProfile(7L);

        assertEquals("메모노기", response.nickname());
        assertEquals(3, response.verificationCount());
        assertEquals(1, response.mainChannel());
        assertEquals("HUMAN", response.race());
        assertEquals("255.128.0", response.preferredColors().getFirst());
    }

    @Test
    void missingOrInactiveUsersUseThePublicNotFoundError() {
        when(users.findByIdAndStatus(9L, Status.ACTIVE)).thenReturn(Optional.empty());
        CustomException exception =
                assertThrows(CustomException.class, () -> service.getPublicProfile(9L));
        assertEquals(GlobalExceptionCode.PUBLIC_USER_PROFILE_NOT_FOUND, exception.getCode());
    }
}
