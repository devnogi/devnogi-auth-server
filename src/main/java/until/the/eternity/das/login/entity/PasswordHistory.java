package until.the.eternity.das.login.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import until.the.eternity.das.user.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PasswordHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("비밀번호 이력 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @Comment("사용자 ID")
    private User user;

    @Column(name = "previous_password_hash", nullable = false, length = 255)
    @Comment("이전에 사용했던 암호화된 비밀번호")
    private String previousPasswordHash;

    @Column(name = "changed_at", nullable = false)
    @Comment("비밀번호 변경 시각")
    private LocalDateTime changedAt;

}