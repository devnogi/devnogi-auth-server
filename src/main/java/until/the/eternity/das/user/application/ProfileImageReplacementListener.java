package until.the.eternity.das.user.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import until.the.eternity.das.common.application.S3Service;

@Component
@RequiredArgsConstructor
public class ProfileImageReplacementListener {

    private final S3Service s3Service;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deletePreviousImageAfterCommit(ProfileImageReplacementEvent event) {
        s3Service.deleteImageQuietly(event.previousImageUrl());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void deleteReplacementImageAfterRollback(ProfileImageReplacementEvent event) {
        s3Service.deleteImageQuietly(event.replacementImageUrl());
    }
}
