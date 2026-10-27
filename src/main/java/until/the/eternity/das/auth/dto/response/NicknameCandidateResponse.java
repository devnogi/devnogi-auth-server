package until.the.eternity.das.auth.dto.response;

import java.time.Instant;

public record NicknameCandidateResponse(String nickname, String nicknameTicket, Instant expiresAt) {
}
