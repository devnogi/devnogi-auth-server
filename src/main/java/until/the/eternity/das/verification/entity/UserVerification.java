package until.the.eternity.das.verification.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import until.the.eternity.das.user.entity.User;

@Entity
@Table(name = "user_verification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserVerification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(name = "server_name", length = 20)
  private String serverName;

  @Column(name = "character_name", length = 100)
  private String characterName;

  @Column(name = "last_verified_at")
  private LocalDateTime lastVerifiedAt;

  @Column(name = "verification_count", nullable = false)
  private int verificationCount;

  @Column(name = "latest_token_id")
  private Long latestTokenId;

  @Column(name = "is_verified", nullable = false)
  private boolean verified;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public boolean hasRecentSuccess(LocalDateTime threshold) {
    return lastVerifiedAt != null && !lastVerifiedAt.isBefore(threshold);
  }

  public boolean matchesIdentity(String serverName, String characterName) {
    return this.serverName != null && this.characterName != null
      && this.serverName.equals(serverName)
      && this.characterName.equals(characterName);
  }

  public void markVerified(String serverName, String characterName, LocalDateTime now, Long latestTokenId) {
    if (matchesIdentity(serverName, characterName)) {
      this.verificationCount = this.verificationCount + 1;
    } else {
      this.verificationCount = 1;
    }

    this.serverName = serverName;
    this.characterName = characterName;
    this.lastVerifiedAt = now;
    this.latestTokenId = latestTokenId;
    this.verified = true;
  }

  public void invalidate() {
    this.verified = false;
  }
}
