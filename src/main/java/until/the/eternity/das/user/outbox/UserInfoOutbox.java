package until.the.eternity.das.user.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import until.the.eternity.das.user.dto.response.UserInfoUpdateEvent;
import until.the.eternity.das.user.entity.User;

@Entity
@Table(name = "user_info_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserInfoOutbox {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private long revision;

    @Column(name = "published_revision", nullable = false)
    private long publishedRevision;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "profile_image_url", length = 512)
    private String profileImageUrl;

    @Column(name = "server_name", length = 20)
    private String serverName;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "claim_token", length = 36)
    private String claimToken;

    @Column(name = "claim_until")
    private LocalDateTime claimUntil;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static UserInfoOutbox first(User user, LocalDateTime now) {
        UserInfoOutbox outbox = new UserInfoOutbox();
        outbox.userId = user.getId();
        outbox.revision = 1;
        outbox.publishedRevision = 0;
        outbox.copySnapshot(user);
        outbox.attemptCount = 0;
        outbox.nextAttemptAt = now;
        outbox.createdAt = now;
        outbox.updatedAt = now;
        return outbox;
    }

    public void replaceWith(User user, LocalDateTime now) {
        revision++;
        copySnapshot(user);
        attemptCount = 0;
        nextAttemptAt = now;
        updatedAt = now;
    }

    public ClaimedUserInfoUpdate claim(String token, LocalDateTime until, LocalDateTime now) {
        claimToken = token;
        claimUntil = until;
        updatedAt = now;
        return new ClaimedUserInfoUpdate(
                userId,
                revision,
                attemptCount,
                token,
                new UserInfoUpdateEvent(
                        userId, nickname, profileImageUrl, serverName, verified, revision));
    }

    private void copySnapshot(User user) {
        nickname = user.getNickname();
        profileImageUrl = user.getProfileImageUrl();
        serverName = user.getServerName();
        verified = user.isVerified();
    }
}
