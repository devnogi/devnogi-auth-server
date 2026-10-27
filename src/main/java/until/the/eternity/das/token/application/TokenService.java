package until.the.eternity.das.token.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import until.the.eternity.das.auth.dto.response.LoginResultResponse;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.common.util.JwtUtil;
import until.the.eternity.das.token.entity.RefreshToken;
import until.the.eternity.das.token.entity.RefreshTokenRepository;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.enums.Status;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtUtil jwtUtil;
  private final EntityManager entityManager;

  /**
   * 사용자를 위해 새로운 리프레시 토큰을 발급하고 저장합니다.
   *
   * @param userId 사용자의 ID
   * @param token 새로 발급된 리프레시 토큰 문자열
   */
  @Transactional
  public void saveNewRefreshToken(Long userId, String token) {
    User user = lockUser(userId);

    RefreshToken newRefreshToken = RefreshToken.builder()
      .user(user)
      .token(token)
      .expiresAt(jwtUtil.getExpirationDateFromToken(token))
      .createdAt(LocalDateTime.now())
      .revoked(false)
      .build();

    refreshTokenRepository.save(newRefreshToken);
  }

  @Transactional
  public LoginResultResponse refresh(String refreshTokenStr) {
    // 1. JWT 서명/만료 검증
    jwtUtil.validateToken(refreshTokenStr);

    // 2. Claims에서 type == "REFRESH" 확인
    var claims = jwtUtil.extractAllClaims(refreshTokenStr);
    if (!"REFRESH".equals(claims.get("type", String.class))) {
      throw new CustomException(GlobalExceptionCode.INVALID_REFRESH_TOKEN);
    }

    // 3. Lock the user first, matching the logout lock order.
    Long userId = claims.get("userId", Long.class);
    User user = lockUser(userId);

    if (user.getStatus() != Status.ACTIVE) {
      throw new CustomException(GlobalExceptionCode.USER_DISABLED);
    }

    // 4. A single conditional update rejects replay and duplicate legacy rows.
    // A non-one result throws and rolls back the entire update transaction.
    if (refreshTokenRepository.revokeActiveToken(refreshTokenStr, user) != 1) {
      throw new CustomException(GlobalExceptionCode.INVALID_REFRESH_TOKEN);
    }

    // 5. 새 access token + 새 refresh token 발급 (토큰 로테이션)
    String newAccessToken = jwtUtil.generateAccessToken(user);
    String newRefreshToken = jwtUtil.generateRefreshToken(user);

    // 6. 새 refresh token DB 저장
    saveNewRefreshToken(userId, newRefreshToken);

    // 7. LoginResultResponse 반환
    return LoginResultResponse.builder()
      .user(user)
      .accessToken(newAccessToken)
      .refreshToken(newRefreshToken)
      .build();
  }

  @Transactional
  public void revokeAllUserTokens(Long userId) {
    User user = lockUser(userId);

    List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUserAndRevokedFalse(user);
    activeTokens.forEach(RefreshToken::revoke);
  }

  private User lockUser(Long userId) {
    User user = entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
    if (user == null) {
      throw new CustomException(GlobalExceptionCode.USER_NOT_EXISTS);
    }
    return user;
  }
}
