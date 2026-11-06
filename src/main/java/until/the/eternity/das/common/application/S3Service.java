package until.the.eternity.das.common.application;

import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Template s3Template;

    @Value("${S3_BUCKET}")
    private String bucket;

    public String uploadImage(MultipartFile file, String dirName) {

        if (file == null || file.isEmpty()) {
            throw new CustomException(GlobalExceptionCode.FILE_EMPTY);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("/yyyy/MM/dd/HH/");
        String subDir = sdf.format(new Date());

        String fileName = dirName + subDir + UUID.randomUUID() + "_" + file.getOriginalFilename();

        try {
            S3Resource resource = s3Template.upload(bucket, fileName, file.getInputStream());

            return resource.getURL().toString();
        } catch (IOException e) {
            log.error("S3 image upload failed. bucket={}", bucket, e);
            throw new CustomException(GlobalExceptionCode.FILE_UPLOAD_FAILED);
        } catch (RuntimeException e) {
            log.error("S3 image upload failed. bucket={}", bucket, e);
            throw new CustomException(GlobalExceptionCode.FILE_UPLOAD_FAILED);
        }
    }

    // 이미지 삭제
    public void deleteImage(String imageUrl) {
        try {
            String key = deleteObject(imageUrl);
            log.info("S3 이미지 삭제 완료. Key: {}", key);
        } catch (Exception e) {
            log.error("S3 image deletion failed", e);
            throw new CustomException(GlobalExceptionCode.FILE_DELETE_FAILED);
        }
    }

    public void deleteImageQuietly(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        try {
            String key = deleteObject(imageUrl);
            log.info("S3 deferred image cleanup completed. Key: {}", key);
        } catch (Exception e) {
            log.error("S3 deferred image cleanup failed", e);
        }
    }

    private String deleteObject(String imageUrl) throws URISyntaxException {
        URI uri = new URI(imageUrl);
        String path = uri.getPath();

        String key =
                path.startsWith("/" + bucket + "/")
                        ? path.substring(bucket.length() + 2)
                        : path.substring(1);

        s3Template.deleteObject(bucket, key);
        return key;
    }
}
