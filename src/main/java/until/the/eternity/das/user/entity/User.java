package until.the.eternity.das.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import until.the.eternity.das.role.entity.Role;
import until.the.eternity.das.user.entity.enums.InactivatedType;
import until.the.eternity.das.user.entity.enums.Status;

import java.time.LocalDateTime;

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

  @Column(name = "password_hash", length = 255)
  @Comment("암호화된 비밀번호 (소셜로그인 회원은 null)")
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
  @CreationTimestamp
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  @Comment("계정 정보 수정일")
  @UpdateTimestamp
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

  @Column(name = "server_name", length = 20)
  @Comment("게임 서버명")
  private String serverName;

  @Column(name = "is_verified", nullable = false)
  @Comment("사용자 인증 상태")
  private boolean verified;

  @Column(name = "verified_at")
  @Comment("최근 인증 성공 시각")
  private LocalDateTime verifiedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "role_id")
  private Role role;

  public void updateUserInfo(String nickname, String profileImageUrl) {
    this.nickname = nickname;
    this.profileImageUrl = profileImageUrl;
  }

  public void updatePassword(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public void updateLastLoginAt() {
    this.lastLoginAt = LocalDateTime.now();
  }

  public void updateUserStatus(Status status) {
    this.status = status;
    this.inactivatedAt = LocalDateTime.now();
  }

  public void updateServerName(String serverName) {
    this.serverName = serverName;
  }

  public void updateVerificationStatus(boolean verified, LocalDateTime verifiedAt) {
    this.verified = verified;
    this.verifiedAt = verifiedAt;
  }

  public void updateGameProfile(String nickname, String serverName) {
    this.nickname = nickname;
    this.serverName = serverName;
  }

}
