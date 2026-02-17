package until.the.eternity.das.verification.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.verification.entity.enums.VerificationFailureReason;

@Entity
@Table(name = "user_verification_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserVerificationHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "server_name", length = 20)
  private String serverName;

  @Column(name = "character_name", length = 100)
  private String characterName;

  @Column(name = "verified_at", nullable = false)
  private LocalDateTime verifiedAt;

  @Column(name = "verification_success", nullable = false)
  private boolean verificationSuccess;

  @Enumerated(EnumType.STRING)
  @Column(name = "failure_reason", length = 100)
  private VerificationFailureReason failureReason;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "token_id")
  private UserVerificationToken token;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
