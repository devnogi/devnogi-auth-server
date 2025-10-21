package until.the.eternity.das.user.dto.request;

import org.springframework.web.multipart.MultipartFile;

public record UserInfoUpdateRequest(
  String nickname,

  MultipartFile file
) {
}
