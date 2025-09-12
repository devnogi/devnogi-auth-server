package until.the.eternity.das.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import until.the.eternity.das.common.constant.JwtConstant;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.user.entity.User;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

  private final JwtConstant jwtConstant;

  private SecretKey getSecretKey() {
    byte[] keyBytes = Decoders.BASE64.decode(jwtConstant.getSecret());
    return Keys.hmacShaKeyFor(keyBytes);
  }

  /**
   * 엑세스 토큰 생성
   *
   * @param user
   * @return
   */
  public String generateAccessToken(User user) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("userId", user.getId());
    claims.put("role", user.getRole()
      .getName());
    claims.put("type", "ACCESS");

    return createToken(claims, user.getEmail(), jwtConstant.getAccessTokenExpiration());
  }

  /**
   * 리프레쉬 토큰 생성
   *
   * @param user
   * @return
   */
  public String generateRefreshToken(User user) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("userId", user.getId());
    claims.put("type", "REFRESH");

    return createToken(claims, user.getEmail(), jwtConstant.getRefreshTokenExpiration());
  }

  /**
   * JWT 토큰 생성 메서드
   *
   * @param subject
   * @param expiration
   * @return
   */
  private String createToken(Map<String, Object> claims, String subject, long expiration) {
    Date now = new Date();
    Date expirationDate = new Date(now.getTime() + expiration);

    return Jwts.builder()
      .claims(claims)
      .subject(subject)
      .issuer(jwtConstant.getISSUER())
      .issuedAt(now)
      .expiration(expirationDate)
      .signWith(getSecretKey())
      .compact();
  }

  /**
   * 토큰에서 Claims 추출
   *
   * @param token
   * @return
   */
  public Claims extractAllClaims(String token) {
    return Jwts.parser()
      .verifyWith(getSecretKey())
      .build()
      .parseSignedClaims(token)
      .getPayload();
  }

  /**
   * 쿠키에서 리프레쉬토큰 추출
   *
   * @param request
   * @return
   */
  public String extractRefreshToken(HttpServletRequest request) {
    if (request.getCookies() == null) return null;

    return Arrays.stream(request.getCookies())
      .filter(cookie -> "refresh_token".equals(cookie.getName()))
      .map(Cookie::getValue)
      .findFirst()
      .orElse(null);
  }

  /**
   * 토큰에서 사용자 ID 추출
   *
   * @param token
   * @return
   */
  public Long getUserIdFromToken(String token) {
    Claims claims = extractAllClaims(token);
    return claims.get("userId", Long.class);
  }

  /**
   * 토큰 유효성 검증
   *
   * @param token
   * @return
   */
  public boolean validateToken(String token) {
    try {
      extractAllClaims(token);
      return true;
    } catch (MalformedJwtException | UnsupportedJwtException | IllegalArgumentException e) {
      throw new CustomException(GlobalExceptionCode.INVALID_TOKEN);
    } catch (ExpiredJwtException e) {
      throw new CustomException(GlobalExceptionCode.EXPIRED_TOKEN);
    }
  }
}
