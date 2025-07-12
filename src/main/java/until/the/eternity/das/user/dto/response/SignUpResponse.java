package until.the.eternity.das.user.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record SignUpResponse(

    @Schema(description = "사용자 고유 ID", example = "1", requiredMode = REQUIRED)
    Long id
) {

  public static SignUpResponse of(Long id) {
    return SignUpResponse.builder()
        .id(id)
        .build();
  }
}
