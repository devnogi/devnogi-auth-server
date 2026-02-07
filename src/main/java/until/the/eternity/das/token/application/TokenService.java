package until.the.eternity.das.token.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.common.util.JwtUtil;
import until.the.eternity.das.token.entity.RefreshToken;
import until.the.eternity.das.token.entity.RefreshTokenRepository;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

  private final RefreshTokenRepository refreshTokenRepository;
  private final UserRepository userRepository;
  private final JwtUtil jwtUtil;

  /**
   * 사용자를 위해 새로운 리프레시 토큰을 발급하고 저장합니다.
   *
   * @param userId 사용자의 ID
   * @param token 새로 발급된 리프레시 토큰 문자열
   */
  @Transactional
  public void saveNewRefreshToken(Long userId, String token) {
    User user = userRepository.findById(userId)
      .orElseThrow(() -> new CustomException(GlobalExceptionCode.USER_NOT_EXISTS));

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
  public void revokeAllUserTokens(Long userId) {
    User user = userRepository.findById(userId)
      .orElseThrow(() -> new CustomException(GlobalExceptionCode.USER_NOT_EXISTS));

    List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUserAndRevokedFalse(user);
    activeTokens.forEach(RefreshToken::revoke);
  }
}