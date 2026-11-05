package until.the.eternity.das.user.outbox;

import until.the.eternity.das.user.dto.response.UserInfoUpdateEvent;

public record ClaimedUserInfoUpdate(
        Long userId,
        long revision,
        int attemptCount,
        String claimToken,
        UserInfoUpdateEvent event) {}
