package until.the.eternity.das.verification.dto.response;

import java.util.List;

public record UserVerificationHistoryListResponse(
  String sort,
  int limit,
  int count,
  List<UserVerificationHistoryResponse> items
) {
}
