package until.the.eternity.das.user.dto.request;

import org.springframework.web.multipart.MultipartFile;

public record UserInfoUpdateRequest(
        String nickname,
        String nicknameTicket,
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
    public UserInfoUpdateRequest(String nickname, String nicknameTicket, MultipartFile file) {
        this(nickname, nicknameTicket, file, null, null, null, null, null, null, null, null);
    }
}
