package until.the.eternity.das.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import until.the.eternity.das.common.application.S3Service;

@ExtendWith(MockitoExtension.class)
class ProfileImageReplacementListenerTest {

    @Mock private S3Service s3Service;
    @InjectMocks private ProfileImageReplacementListener listener;

    @Test
    void committedReplacementDeletesOnlyPreviousImage() throws Exception {
        ProfileImageReplacementEvent event =
                new ProfileImageReplacementEvent(
                        "https://storage.example/old", "https://storage.example/new");

        listener.deletePreviousImageAfterCommit(event);

        verify(s3Service).deleteImageQuietly("https://storage.example/old");
        assertEquals(
                TransactionPhase.AFTER_COMMIT, listenerPhase("deletePreviousImageAfterCommit"));
    }

    @Test
    void rolledBackReplacementDeletesOnlyNewImage() throws Exception {
        ProfileImageReplacementEvent event =
                new ProfileImageReplacementEvent(
                        "https://storage.example/old", "https://storage.example/new");

        listener.deleteReplacementImageAfterRollback(event);

        verify(s3Service).deleteImageQuietly("https://storage.example/new");
        assertEquals(
                TransactionPhase.AFTER_ROLLBACK,
                listenerPhase("deleteReplacementImageAfterRollback"));
    }

    private TransactionPhase listenerPhase(String methodName) throws Exception {
        Method method =
                ProfileImageReplacementListener.class.getMethod(
                        methodName, ProfileImageReplacementEvent.class);
        return method.getAnnotation(TransactionalEventListener.class).phase();
    }
}
