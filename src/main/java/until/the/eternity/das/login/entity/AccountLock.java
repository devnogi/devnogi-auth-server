package until.the.eternity.das.login.entity;



import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import until.the.eternity.das.user.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "account_locks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AccountLock {

    @Id
    @Column(name = "user_id")
    @Comment("사용자 ID")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "failed_attempts", nullable = false)
    @Builder.Default
    @Comment("실패 시도 횟수")
    private Integer failedAttempts = 0;

    @Column(name = "locked_until")
    @Comment("잠금 해제 예정 시각")
    private LocalDateTime lockedUntil;

    @Column(name = "updated_at", nullable = false)
    @Comment("최근 업데이트 시각")
    private LocalDateTime updatedAt;
}