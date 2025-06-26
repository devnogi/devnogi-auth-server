package until.the.eternity.das.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import until.the.eternity.das.user.entity.enums.InactivatedType;
import until.the.eternity.das.user.entity.enums.Status;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("사용자 고유 ID")
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    @Comment("사용자 이메일 (로그인 ID)")
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    @Comment("암호화된 비밀번호")
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 50)
    @Comment("사용자 닉네임 (커뮤니티 표시용)")
    private String nickname;

    @Column(name = "profile_image_url", length = 512)
    @Comment("프로필 이미지 URL")
    private String profileImageUrl;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @Comment("계정 상태")
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("계정 생성일")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Comment("계정 정보 수정일")
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    @Comment("마지막 로그인 시각")
    private LocalDateTime lastLoginAt;

    @Column(name = "inactivated_at")
    @Comment("계정 비활성화 일시")
    private LocalDateTime inactivatedAt;

    @Column(name = "inactivated_type", length = 255)
    @Comment("계정 비활성화 타입 (탈퇴, 장기미접속, 불특정IP접속)")
    @Enumerated(EnumType.STRING)
    private InactivatedType inactivatedType;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id")
    private List<UserRole> roleList;
}