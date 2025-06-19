package until.the.eternity.das.token.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import until.the.eternity.das.user.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_verification_tokens",
        indexes = {
                @Index(name = "user_id", columnList = "user_id"),
                @Index(name = "idx_verification_token", columnList = "token")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("토큰 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @Comment("사용자 ID")
    private User user;

    @Column(nullable = false, length = 255)
    @Comment("이메일 인증 또는 초기화용 토큰")
    private String token;

    public enum TokenType {
        EMAIL_VERIFICATION,
        PASSWORD_RESET
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Comment("토큰 종류")
    private TokenType type;

    @Column(name = "expires_at", nullable = false)
    @Comment("만료 시각")
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    @Comment("사용 여부")
    private Boolean used = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("생성 시각")
    private LocalDateTime createdAt;
}