package until.the.eternity.das.common.application;

import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

  // S3Client 대신 S3Template을 주입받습니다.
  private final S3Template s3Template;

  // application.yml에 버킷 이름을 설정하는 대신, 파라미터로 받도록 변경합니다. (더 유연한 방식)
  @Value("${S3_BUCKET}")
  private String bucket;

  // 이미지 업로드
  public String uploadImage(MultipartFile file, String dirName) {

    if (file == null || file.isEmpty()) {
      throw new CustomException(GlobalExceptionCode.FILE_EMPTY);
    }

    // 날짜를 포함한 폴더 경로 생성
    SimpleDateFormat sdf = new SimpleDateFormat("/yyyy/MM/dd/HH/");
    String subDir = sdf.format(new Date());

    // 최종 파일 경로 및 이름 생성 (key)
    String fileName = dirName + subDir + UUID.randomUUID() + "_" + file.getOriginalFilename();

    try {
      S3Resource resource = s3Template.upload(bucket, fileName, file.getInputStream());

      return resource.getURL()
        .toString();
    } catch (IOException e) {
      log.error("S3 이미지 업로드 실패: {}", e.getMessage());
      throw new CustomException(GlobalExceptionCode.FILE_UPLOAD_FAILED);
    }
  }

  // 이미지 삭제
  public void deleteImage(String imageUrl) {
    try {
      String key = imageUrl.substring(imageUrl.indexOf("com/") + 4);

      s3Template.deleteObject(bucket, key);
    } catch (Exception e) {
      log.error("S3 이미지 삭제 실패: {}, 이미지 URL: {}", e.getMessage(), imageUrl);
      throw new CustomException(GlobalExceptionCode.FILE_DELETE_FAILED);
    }
  }
}
