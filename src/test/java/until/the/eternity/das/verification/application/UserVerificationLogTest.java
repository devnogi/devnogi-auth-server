package until.the.eternity.das.verification.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import until.the.eternity.das.common.application.KafkaProducerService;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.user.application.UserService;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;
import until.the.eternity.das.verification.entity.UserVerificationHistoryRepository;
import until.the.eternity.das.verification.entity.UserVerificationRepository;
import until.the.eternity.das.verification.entity.UserVerificationToken;
import until.the.eternity.das.verification.entity.UserVerificationTokenRepository;
import until.the.eternity.das.verification.kafka.UserVerificationKafkaConsumer;

class UserVerificationLogTest {

  private static final String TOKEN_MARKER = "SYNTHETIC_SECRET_TOKEN_MARKER";
  private static final String CHARACTER_MARKER = "SYNTHETIC_CHARACTER_MARKER";
  private static final String SERVER_MARKER = "SYNTHETIC_SERVER_MARKER";
  private static final String MESSAGE_MARKER = "SYNTHETIC_MESSAGE_MARKER";

  private UserVerificationService service;
  private UserVerificationTokenRepository tokens;
  private UserVerificationRepository verifications;

  @BeforeEach
  void setUp() {
    tokens = mock(UserVerificationTokenRepository.class);
    verifications = mock(UserVerificationRepository.class);
    service = new UserVerificationService(
      mock(UserRepository.class),
      tokens,
      verifications,
      mock(UserVerificationHistoryRepository.class),
      mock(KafkaProducerService.class),
      new ObjectMapper().findAndRegisterModules(),
      mock(UserVerificationHistoryService.class),
      mock(UserService.class),
      mock(EntityManager.class)
    );
    ReflectionTestUtils.setField(service, "verificationPrefix", "메모노기_");
  }

  @Test
  void malformedPayloadDoesNotEnterWarningOrResultLogs() {
    try (LogCapture logs = new LogCapture(UserVerificationService.class)) {
      service.verifyFromKafkaPayload("{\"verificationValue\":\"" + TOKEN_MARKER + "\",broken");

      assertTrue(logs.messages().contains("INVALID_MESSAGE"));
      assertNoMarkers(logs.messages());
    }
  }

  @Test
  void unknownAndRevokedTokensDoNotEnterFailureLogs() {
    try (LogCapture logs = new LogCapture(UserVerificationService.class)) {
      when(tokens.findByTokenValue(TOKEN_MARKER)).thenReturn(Optional.empty());
      service.verifyFromKafkaPayload(validPayload());
      assertTrue(logs.messages().contains("TOKEN_NOT_FOUND"));

      User user = mock(User.class);
      when(user.getId()).thenReturn(42L);
      UserVerificationToken revoked = mock(UserVerificationToken.class);
      when(revoked.getUser()).thenReturn(user);
      when(revoked.isRevoked()).thenReturn(true);
      when(tokens.findByTokenValue(TOKEN_MARKER)).thenReturn(Optional.of(revoked));
      service.verifyFromKafkaPayload(validPayload());

      assertTrue(logs.messages().contains("TOKEN_REVOKED"));
      assertNoMarkers(logs.messages());
    }
  }

  @Test
  void expiredTokenDoesNotEnterFailureLogs() {
    try (LogCapture logs = new LogCapture(UserVerificationService.class)) {
      User user = mock(User.class);
      when(user.getId()).thenReturn(42L);
      UserVerificationToken expired = mock(UserVerificationToken.class);
      when(expired.getUser()).thenReturn(user);
      when(expired.isExpired(any(LocalDateTime.class))).thenReturn(true);
      when(tokens.findByTokenValue(TOKEN_MARKER)).thenReturn(Optional.of(expired));

      service.verifyFromKafkaPayload(validPayload());

      assertTrue(logs.messages().contains("TOKEN_EXPIRED"));
      assertNoMarkers(logs.messages());
    }
  }

  @Test
  void successfulVerificationLogsOnlyResultAndSafeUserId() {
    try (LogCapture logs = new LogCapture(UserVerificationService.class)) {
      User user = mock(User.class);
      when(user.getId()).thenReturn(42L);
      UserVerificationToken token = mock(UserVerificationToken.class);
      when(token.getUser()).thenReturn(user);
      when(tokens.findByTokenValue(TOKEN_MARKER)).thenReturn(Optional.of(token));
      when(verifications.findByServerNameAndCharacterNameAndVerifiedTrue(SERVER_MARKER, CHARACTER_MARKER))
        .thenReturn(Optional.empty());
      when(verifications.findByUserId(42L)).thenReturn(Optional.empty());

      service.verifyFromKafkaPayload(validPayload());

      assertTrue(logs.messages().contains("result=VERIFIED, userId=42"));
      assertNoMarkers(logs.messages());
    }
  }

  @Test
  void exceptionMessageCannotEnterErrorLog() {
    try (LogCapture logs = new LogCapture(UserVerificationService.class)) {
      when(tokens.findByTokenValue(anyString())).thenThrow(new IllegalStateException(TOKEN_MARKER));

      assertThrows(CustomException.class, () -> service.verifyFromKafkaPayload(validPayload()));

      assertTrue(logs.messages().contains("exceptionType=IllegalStateException"));
      assertNoMarkers(logs.messages());
    }
  }

  @Test
  void consumerDebugLogDoesNotExposeForwardedPayload() {
    UserVerificationService receiver = mock(UserVerificationService.class);
    UserVerificationKafkaConsumer consumer = new UserVerificationKafkaConsumer(receiver);

    try (LogCapture logs = new LogCapture(UserVerificationKafkaConsumer.class)) {
      consumer.consumeVerificationMessage(TOKEN_MARKER);

      verify(receiver).verifyFromKafkaPayload(TOKEN_MARKER);
      assertTrue(logs.messages().contains("Received user verification kafka message"));
      assertNoMarkers(logs.messages());
    }
  }

  private static String validPayload() {
    return """
      {"characterName":"%s","serverName":"%s","verificationValue":"%s","message":"%s","dateSend":"2026-01-01T00:00:00Z"}
      """.formatted(CHARACTER_MARKER, SERVER_MARKER, TOKEN_MARKER, MESSAGE_MARKER);
  }

  private static void assertNoMarkers(String messages) {
    assertFalse(messages.contains(TOKEN_MARKER));
    assertFalse(messages.contains(CHARACTER_MARKER));
    assertFalse(messages.contains(SERVER_MARKER));
    assertFalse(messages.contains(MESSAGE_MARKER));
  }

  private static final class LogCapture implements AutoCloseable {
    private final Logger logger;
    private final Level originalLevel;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(Class<?> type) {
      logger = (Logger) LoggerFactory.getLogger(type);
      originalLevel = logger.getLevel();
      logger.setLevel(Level.DEBUG);
      appender.start();
      logger.addAppender(appender);
    }

    private String messages() {
      return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
        .reduce("", (left, right) -> left + "\n" + right);
    }

    @Override
    public void close() {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
      appender.stop();
    }
  }
}
