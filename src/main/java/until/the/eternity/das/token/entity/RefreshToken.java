package until.the.eternity.das.token.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

  /**
   * 이 토큰을 철회(무효화) 상태로 변경합니다.
   */
  public void revoke() {
    this.revoked = true;
  }

}