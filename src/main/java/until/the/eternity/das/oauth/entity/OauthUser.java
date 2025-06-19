package until.the.eternity.das.oauth.entity;



import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import until.the.eternity.das.user.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "oauth_users",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OauthUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("소셜 연동 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @Comment("사용자 ID")
    private User user;

    @Column(nullable = false, length = 50)
    @Comment("소셜 제공자 (google, kakao 등)")
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    @Comment("제공자에서의 사용자 고유 ID")
    private String providerUserId;

    @Column(name = "linked_at", nullable = false)
    @Comment("연동된 시각")
    private LocalDateTime linkedAt;
}