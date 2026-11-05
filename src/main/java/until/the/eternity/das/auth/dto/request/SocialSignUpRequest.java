package until.the.eternity.das.auth.dto.request;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;
import until.the.eternity.das.user.dto.request.GameProfileInput;

public record SocialSignUpRequest(
        String provider,
        String providerUserId,
        @Schema(description = "구형 클라이언트 호환 필드. 신원 출처로 사용하지 않음") String email,
        @Schema(description = "사용자 닉네임 (커뮤니티 표시용)", example = "abc1234", requiredMode = REQUIRED)
                String nickname,
        @Schema(description = "랜덤 닉네임 후보 발급 티켓") String nicknameTicket,
        @Schema(description = "OAuth 교환 뒤 서버가 발급한 일회용 가입 증명") String signupProof,
        MultipartFile file,
        Integer mainChannel,
        String guildName,
        String race,
        String arcana,
        String preferredColor1,
        String preferredColor2,
        String mbti,
        String bio)
        implements GameProfileInput {
    public SocialSignUpRequest(
            String provider,
            String providerUserId,
            String email,
            String nickname,
            String nicknameTicket,
            String signupProof,
            MultipartFile file) {
        this(
                provider,
                providerUserId,
                email,
                nickname,
                nicknameTicket,
                signupProof,
                file,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
