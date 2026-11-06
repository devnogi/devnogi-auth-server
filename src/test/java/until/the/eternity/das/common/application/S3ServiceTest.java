package until.the.eternity.das.common.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock private S3Template s3Template;
    @Mock private MultipartFile file;
    @Mock private S3Resource resource;
    @InjectMocks private S3Service service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "bucket", "auth-profile");
    }

    @Test
    void returnsUploadedResourceUrl() throws Exception {
        prepareFile();
        when(s3Template.upload(eq("auth-profile"), anyString(), any(ByteArrayInputStream.class)))
                .thenReturn(resource);
        when(resource.getURL())
                .thenReturn(new URL("https://storage.example/auth-profile/profile.png"));

        assertEquals(
                "https://storage.example/auth-profile/profile.png",
                service.uploadImage(file, "profile"));
    }

    @Test
    void mapsStorageRuntimeFailureToFileUploadError() throws IOException {
        prepareFile();
        when(s3Template.upload(eq("auth-profile"), anyString(), any(ByteArrayInputStream.class)))
                .thenThrow(new IllegalStateException("synthetic storage failure"));

        CustomException exception =
                assertThrows(CustomException.class, () -> service.uploadImage(file, "profile"));

        assertEquals(GlobalExceptionCode.FILE_UPLOAD_FAILED, exception.getCode());
    }

    @Test
    void deferredCleanupFailureDoesNotReplaceTransactionOutcome() {
        doThrow(new IllegalStateException("synthetic cleanup failure"))
                .when(s3Template)
                .deleteObject("auth-profile", "profile.png");

        assertDoesNotThrow(
                () ->
                        service.deleteImageQuietly(
                                "https://storage.example/auth-profile/profile.png"));
    }

    private void prepareFile() throws IOException {
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("profile.png");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] {1}));
    }
}
