package until.the.eternity.das.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import until.the.eternity.das.auth.application.NicknameCandidateService;
import until.the.eternity.das.common.application.S3Service;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.user.dto.request.UserInfoUpdateRequest;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;
import until.the.eternity.das.user.outbox.UserInfoOutboxService;
import until.the.eternity.das.verification.entity.UserVerificationRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceProfileImageTest {

    private static final String OLD_IMAGE = "https://storage.example/auth-profile/old.png";
    private static final String NEW_IMAGE = "https://storage.example/auth-profile/new.png";

    @Mock private UserRepository users;
    @Mock private S3Service images;
    @Mock private UserInfoOutboxService outbox;
    @Mock private NicknameCandidateService candidates;
    @Mock private EntityManager entityManager;
    @Mock private GameProfileService gameProfiles;
    @Mock private UserVerificationRepository verifications;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private UserService service;

    @Test
    void uploadsReplacementBeforeDeferringPreviousImageCleanup() {
        User user = existingUser();
        MockMultipartFile file =
                new MockMultipartFile("file", "new.png", "image/png", new byte[] {1});
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(images.uploadImage(file, "profile")).thenReturn(NEW_IMAGE);

        service.updateUserInfo(new UserInfoUpdateRequest("기존 닉네임", null, file), 1L);

        ArgumentCaptor<ProfileImageReplacementEvent> event =
                ArgumentCaptor.forClass(ProfileImageReplacementEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(OLD_IMAGE, event.getValue().previousImageUrl());
        assertEquals(NEW_IMAGE, event.getValue().replacementImageUrl());
        assertEquals(NEW_IMAGE, user.getProfileImageUrl());
        verify(images, never()).deleteImage(any());
        verify(images, never()).deleteImageQuietly(any());
        verify(entityManager).refresh(user, LockModeType.PESSIMISTIC_WRITE);
        verify(users).flush();
        verify(outbox).enqueue(user);
    }

    @Test
    void failedUploadPreservesPreviousImageAndDoesNotScheduleCleanup() {
        User user = existingUser();
        MockMultipartFile file =
                new MockMultipartFile("file", "new.png", "image/png", new byte[] {1});
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(images.uploadImage(file, "profile"))
                .thenThrow(new CustomException(GlobalExceptionCode.FILE_UPLOAD_FAILED));

        CustomException exception =
                assertThrows(
                        CustomException.class,
                        () ->
                                service.updateUserInfo(
                                        new UserInfoUpdateRequest("기존 닉네임", null, file), 1L));

        assertEquals(GlobalExceptionCode.FILE_UPLOAD_FAILED, exception.getCode());
        assertEquals(OLD_IMAGE, user.getProfileImageUrl());
        verifyNoInteractions(eventPublisher, outbox);
        verify(users, never()).flush();
    }

    private User existingUser() {
        return User.builder()
                .id(1L)
                .nickname("기존 닉네임")
                .profileImageUrl(OLD_IMAGE)
                .verified(false)
                .build();
    }
}
