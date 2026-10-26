package until.the.eternity.das.auth.application;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import until.the.eternity.das.auth.dto.response.NicknameCandidateResponse;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NicknameCandidateServiceTest {
  private static final String TEST_KEY = "bWVtb25vZ2ktbmlja25hbWUtdGlja2V0LWtleS0yMDI2LWRldg==";
  private static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");

  @Test
  void issuedCandidateContainsMatchingTicketAndUtcExpiration() {
    NicknameCandidateService service = serviceAt(NOW, true);
    NicknameCandidateResponse candidate = service.issue("강인한 인간 ABC12");

    assertEquals("강인한 인간 ABC12", candidate.nickname());
    assertEquals(NOW.plusSeconds(600), candidate.expiresAt());
    service.validateForChange(candidate.nickname(), candidate.nicknameTicket());
  }

  @Test
  void missingTamperedAndMismatchedTicketsAreRejected() {
    NicknameCandidateService service = serviceAt(NOW, true);
    NicknameCandidateResponse candidate = service.issue("강인한 인간 ABC12");

    assertEquals(GlobalExceptionCode.NICKNAME_TICKET_REQUIRED,
      assertThrows(CustomException.class, () -> service.validateForChange(candidate.nickname(), null)).getCode());
    assertEquals(GlobalExceptionCode.INVALID_NICKNAME_TICKET,
      assertThrows(CustomException.class,
        () -> service.validateForChange("강인한 인간 ABC13", candidate.nicknameTicket())).getCode());
    String tampered = candidate.nicknameTicket().substring(0, candidate.nicknameTicket().length() - 1) + "x";
    assertEquals(GlobalExceptionCode.INVALID_NICKNAME_TICKET,
      assertThrows(CustomException.class, () -> service.validateForChange(candidate.nickname(), tampered)).getCode());
  }

  @Test
  void expiredTicketIsRejectedAndCompatibilityOverrideIsExplicit() {
    NicknameCandidateResponse candidate = serviceAt(NOW, true).issue("강인한 인간 ABC12");
    NicknameCandidateService later = serviceAt(NOW.plusSeconds(601), true);

    assertEquals(GlobalExceptionCode.EXPIRED_NICKNAME_TICKET,
      assertThrows(CustomException.class,
        () -> later.validateForChange(candidate.nickname(), candidate.nicknameTicket())).getCode());

    NicknameCandidateService compatibility = serviceAt(NOW, false);
    assertTrue(!compatibility.isEnforceIssuedCandidates());
    compatibility.validateForChange("legacyManual", null);
  }

  @Test
  void ticketForAnotherPurposeCannotBeUsedAsNicknameProof() {
    String otherPurpose = Jwts.builder()
      .issuer("memonogi-nickname")
      .audience().add("memonogi-nickname-candidate").and()
      .claim("purpose", "authentication")
      .claim("nickname", "강인한 인간 ABC12")
      .issuedAt(Date.from(NOW))
      .expiration(Date.from(NOW.plusSeconds(600)))
      .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_KEY)))
      .compact();

    assertEquals(GlobalExceptionCode.INVALID_NICKNAME_TICKET,
      assertThrows(CustomException.class,
        () -> serviceAt(NOW, true).validateForChange("강인한 인간 ABC12", otherPurpose)).getCode());
  }

  @Test
  void missingOrWeakDedicatedKeyFailsFast() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    assertThrows(IllegalArgumentException.class, () -> new NicknameCandidateService("", 600, true, clock));
    assertThrows(IllegalArgumentException.class,
      () -> new NicknameCandidateService("dG9vLXNob3J0", 600, true, clock));
    assertThrows(IllegalArgumentException.class,
      () -> new NicknameCandidateService(TEST_KEY, 600, true, TEST_KEY));
  }

  @Test
  void issuerCannotSignAnArbitraryManualNickname() {
    assertThrows(IllegalArgumentException.class, () -> serviceAt(NOW, true).issue("manualName"));
  }

  private NicknameCandidateService serviceAt(Instant instant, boolean strict) {
    return new NicknameCandidateService(TEST_KEY, 600, strict, Clock.fixed(instant, ZoneOffset.UTC));
  }
}
