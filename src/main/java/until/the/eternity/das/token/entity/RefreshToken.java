package until.the.eternity.das.token.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import until.the.eternity.das.user.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("토큰 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @Comment("사용자 ID")
    private User user;

    @Column(nullable = false, length = 512)
    @Comment("JWT Refresh Token")
    private String token;

    @Column(name = "expires_at", nullable = false)
    @Comment("만료 시각")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("생성 시각")
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @Builder.Default
    @Comment("토큰 사용 철회 여부")
    private Boolean revoked = false;

}