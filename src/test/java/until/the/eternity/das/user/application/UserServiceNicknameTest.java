package until.the.eternity.das.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
import until.the.eternity.das.user.dto.request.UserInfoUpdateRequest;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceNicknameTest {
    @Mock private UserRepository users;
    @Mock private S3Service images;
    @Mock private KafkaProducerService events;
    @Mock private NicknameCandidateService candidates;
    @Mock private EntityManager entityManager;
    @InjectMocks private UserService service;

    @Test
    void oldNicknameIsPreservedWhenOnlyOtherProfileFieldsAreUpdated() {
        User user = User.builder().id(1L).nickname("기존 닉네임").verified(false).build();
        when(users.findById(1L)).thenReturn(Optional.of(user));

        service.updateUserInfo(new UserInfoUpdateRequest("기존 닉네임", null, null), 1L);

        assertEquals("기존 닉네임", user.getNickname());
        verifyNoInteractions(candidates);
        verify(entityManager).refresh(user, LockModeType.PESSIMISTIC_WRITE);
        verify(users).flush();
    }

    @Test
    void changedNicknameNeedsIssuedTicketBeforeSaveOrEvent() {
        User user = User.builder().id(1L).nickname("기존 닉네임").verified(false).build();
        when(users.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new CustomException(GlobalExceptionCode.INVALID_NICKNAME_TICKET))
                .when(candidates)
                .validateForChange("강인한 인간 ABC12", null);

        assertEquals(
                GlobalExceptionCode.INVALID_NICKNAME_TICKET,
                assertThrows(
                                CustomException.class,
                                () ->
                                        service.updateUserInfo(
                                                new UserInfoUpdateRequest(
                                                        "강인한 인간 ABC12", null, null),
                                                1L))
                        .getCode());
        assertEquals("기존 닉네임", user.getNickname());
        verifyNoInteractions(events, images);
    }

    @Test
    void issuedNicknameCanReplaceGrandfatheredName() {
        User user = User.builder().id(1L).nickname("기존 닉네임").verified(false).build();
        when(users.findById(1L)).thenReturn(Optional.of(user));

        service.updateUserInfo(
                new UserInfoUpdateRequest("강인한 인간 ABC12", "issued-ticket", null), 1L);

        assertEquals("강인한 인간 ABC12", user.getNickname());
        verify(candidates).validateForChange("강인한 인간 ABC12", "issued-ticket");
        verify(users).flush();
    }

    @Test
    void verifiedUserCannotChangeNicknameEvenWithTicket() {
        User user = User.builder().id(1L).nickname("캐릭터명").verified(true).build();
        when(users.findById(1L)).thenReturn(Optional.of(user));

        assertEquals(
                GlobalExceptionCode.USER_VERIFICATION_REQUIRED_FOR_IDENTITY_UPDATE,
                assertThrows(
                                CustomException.class,
                                () ->
                                        service.updateUserInfo(
                                                new UserInfoUpdateRequest(
                                                        "강인한 인간 ABC12", "issued-ticket", null),
                                                1L))
                        .getCode());
        verifyNoInteractions(candidates, events, images);
    }
}
