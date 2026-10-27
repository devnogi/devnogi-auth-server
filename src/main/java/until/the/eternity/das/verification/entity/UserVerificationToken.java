package until.the.eternity.das.verification.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import until.the.eternity.das.user.entity.User;

@Entity
@Table(name = "user_verification_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserVerificationToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "issued_at", nullable = false)
  private LocalDateTime issuedAt;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "revoked", nullable = false)
  private boolean revoked;

  @Column(name = "revoked_at")
  private LocalDateTime revokedAt;

  @Column(name = "token_value", nullable = false, unique = true, length = 64)
  @Comment("메모노기_ 접두사를 제외한 토큰 값")
  private String tokenValue;

  @Column(name = "verified", nullable = false)
  private boolean verified;

  @Column(name = "verified_at")
  private LocalDateTime verifiedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public boolean isExpired(LocalDateTime now) {
    return expiresAt.isBefore(now);
  }

  public void revoke(LocalDateTime now) {
    this.revoked = true;
    this.revokedAt = now;
  }

  public void markVerified(LocalDateTime now) {
    this.verified = true;
    this.verifiedAt = now;
  }
}
