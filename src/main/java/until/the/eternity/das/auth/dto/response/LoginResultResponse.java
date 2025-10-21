package until.the.eternity.das.auth.dto.response;

import lombok.Builder;
import until.the.eternity.das.user.entity.User;

@Builder
public record LoginResultResponse(
  User user,
  String accessToken,
  String refreshToken
) {
}
