package until.the.eternity.das.login.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
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

  // 실패 횟수 증가 및 잠금 처리 로직
  public void increaseFailAttempts() {
    this.failedAttempts++;
    this.updatedAt = LocalDateTime.now();
  }

  // 계정 잠금 설정
  public void lockAccount() {
    this.lockedUntil = LocalDateTime.now()
      .plusMinutes(5);
    this.updatedAt = LocalDateTime.now();
  }

  // 로그인 성공 시 상태 초기화
  public void reset() {
    this.failedAttempts = 0;
    this.lockedUntil = null;
    this.updatedAt = LocalDateTime.now();
  }
}