package until.the.eternity.das.login.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import until.the.eternity.das.user.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "login_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("로그인 이력 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @Comment("사용자 ID")
    private User user;

    @Column(name = "login_ip", length = 45)
    @Comment("로그인 시도한 IP 주소")
    private String loginIp;

    @Lob
    @Comment("접속한 브라우저/OS 정보")
    private String userAgent;

    @Column
    @Comment("로그인 성공 여부")
    private Boolean success;

    @Column(length = 255)
    @Comment("실패 사유 (예: wrong_password, locked_account)")
    private String reason;

    @Column(name = "created_at", nullable = false)
    @Comment("로그인 시도 시각")
    private LocalDateTime createdAt;

}