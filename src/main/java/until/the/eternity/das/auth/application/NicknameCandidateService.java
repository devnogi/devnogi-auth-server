package until.the.eternity.das.auth.application;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import until.the.eternity.das.auth.dto.response.NicknameCandidateResponse;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.user.entity.enums.NicknameAdjective;
import until.the.eternity.das.user.entity.enums.NicknameWord;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Service
public class NicknameCandidateService {
  private static final String ISSUER = "memonogi-nickname";
  private static final String AUDIENCE = "memonogi-nickname-candidate";
  private static final String PURPOSE = "nickname-candidate";

  private final SecretKey signingKey;
  private final Clock clock;
  private final long validitySeconds;
  private final boolean enforceIssuedCandidates;

  @Autowired
  public NicknameCandidateService(
    @Value("${app.nickname.ticket-secret}") String encodedSecret,
    @Value("${app.nickname.ticket-validity-seconds:600}") long validitySeconds,
    @Value("${app.nickname.enforce-issued-candidates:true}") boolean enforceIssuedCandidates,
    @Value("${jwt.secret-key:}") String authTokenSecret
  ) {
    this(encodedSecret, validitySeconds, enforceIssuedCandidates, Clock.systemUTC(), authTokenSecret);
  }

  NicknameCandidateService(String encodedSecret, long validitySeconds,
                           boolean enforceIssuedCandidates, Clock clock) {
    this(encodedSecret, validitySeconds, enforceIssuedCandidates, clock, null);
  }

  private NicknameCandidateService(String encodedSecret, long validitySeconds,
                                   boolean enforceIssuedCandidates, Clock clock, String authTokenSecret) {
    if (encodedSecret == null || encodedSecret.isBlank()) {
      throw new IllegalArgumentException("A dedicated nickname ticket secret is required");
    }
    if (encodedSecret.equals(authTokenSecret)) {
      throw new IllegalArgumentException("Nickname ticket secret must differ from the authentication JWT key");
    }
    byte[] secret = Decoders.BASE64.decode(encodedSecret);
    if (secret.length < 32) {
      throw new IllegalArgumentException("Nickname ticket secret must contain at least 32 bytes");
    }
    if (validitySeconds < 1 || validitySeconds > 3600) {
      throw new IllegalArgumentException("Nickname ticket validity must be between 1 and 3600 seconds");
    }
    this.signingKey = Keys.hmacShaKeyFor(secret);
    this.clock = clock;
    this.validitySeconds = validitySeconds;
    this.enforceIssuedCandidates = enforceIssuedCandidates;
  }

  public NicknameCandidateResponse issue(String nickname) {
    if (!isGeneratedNickname(nickname)) {
      throw new IllegalArgumentException("Generated nickname is invalid");
    }
    Instant issuedAt = clock.instant();
    Instant expiresAt = issuedAt.plusSeconds(validitySeconds);
    String ticket = Jwts.builder()
      .issuer(ISSUER)
      .audience().add(AUDIENCE).and()
      .claim("purpose", PURPOSE)
      .claim("nickname", nickname)
      .issuedAt(Date.from(issuedAt))
      .expiration(Date.from(expiresAt))
      .signWith(signingKey)
      .compact();
    return new NicknameCandidateResponse(nickname, ticket, expiresAt);
  }

  public void validateForChange(String nickname, String ticket) {
    if (!enforceIssuedCandidates) {
      return;
    }
    if (nickname == null || nickname.isBlank() || ticket == null || ticket.isBlank()) {
      throw new CustomException(GlobalExceptionCode.INVALID_NICKNAME_TICKET);
    }
    try {
      Claims claims = Jwts.parser()
        .verifyWith(signingKey)
        .requireIssuer(ISSUER)
        .requireAudience(AUDIENCE)
        .require("purpose", PURPOSE)
        .clock(() -> Date.from(clock.instant()))
        .build()
        .parseSignedClaims(ticket)
        .getPayload();
      if (claims.getExpiration() == null || claims.getIssuedAt() == null
        || !nickname.equals(claims.get("nickname", String.class)) || !isGeneratedNickname(nickname)) {
        throw new CustomException(GlobalExceptionCode.INVALID_NICKNAME_TICKET);
      }
    } catch (io.jsonwebtoken.ExpiredJwtException e) {
      throw new CustomException(GlobalExceptionCode.EXPIRED_NICKNAME_TICKET);
    } catch (JwtException | IllegalArgumentException e) {
      throw new CustomException(GlobalExceptionCode.INVALID_NICKNAME_TICKET);
    }
  }

  public boolean isEnforceIssuedCandidates() {
    return enforceIssuedCandidates;
  }

  private boolean isGeneratedNickname(String nickname) {
    if (nickname == null || nickname.length() > 20) {
      return false;
    }
    String[] parts = nickname.split(" ", -1);
    return parts.length == 3
      && NicknameAdjective.contains(parts[0])
      && NicknameWord.contains(parts[1])
      && parts[2].matches("[A-Z0-9]{5}");
  }
}
