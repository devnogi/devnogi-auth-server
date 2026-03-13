package until.the.eternity.das.user.application;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import until.the.eternity.das.common.aop.ActiveUserRequired;
import until.the.eternity.das.common.application.KafkaProducerService;
import until.the.eternity.das.common.application.S3Service;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.user.dto.request.UserInfoUpdateRequest;
import until.the.eternity.das.user.dto.response.UserInfoResponse;
import until.the.eternity.das.user.dto.response.UserInfoUpdateEvent;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;
import until.the.eternity.das.user.entity.enums.NicknameAdjective;
import until.the.eternity.das.user.entity.enums.NicknameWord;
import until.the.eternity.das.user.entity.enums.Status;

import java.util.Objects;
import java.util.Random;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

  private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9 ]{2,30}$");

  private static final String RANDOM_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  private static final int RANDOM_SUFFIX_LENGTH = 5;
  private static final Random RANDOM = new Random();

  private final UserRepository userRepository;
  private final S3Service s3Service;
  private final KafkaProducerService kafkaProducerService;

  @Transactional
  @ActiveUserRequired
  public Boolean updateUserInfo(UserInfoUpdateRequest request, Long userId) {
    User user = userRepository.findById(userId)
      .orElseThrow(() -> new CustomException(GlobalExceptionCode.USER_NOT_EXISTS));

    String nextNickname = resolveNextNickname(request.nickname(), user.getNickname());

    boolean wantsNicknameChange = !Objects.equals(nextNickname, user.getNickname());
    ensureIdentityUpdateAllowed(user, wantsNicknameChange, false);
    if (wantsNicknameChange) {
      validateNickname(nextNickname);
      validateNicknameCombination(nextNickname);
      ensureNicknameAvailable(nextNickname, userId);
    }

    String profileImageUrl = user.getProfileImageUrl();

    if (request.file() != null) {
      if (user.getProfileImageUrl() != null) {
        s3Service.deleteImage(user.getProfileImageUrl());
      }

      String dirName = "profile";
      profileImageUrl = s3Service.uploadImage(request.file(), dirName);
    }

    try {
      user.updateUserInfo(nextNickname, profileImageUrl);

      UserInfoUpdateEvent kafKaEvent = UserInfoUpdateEvent.of(user);

      kafkaProducerService.sendUserInfoUpdateEvent(kafKaEvent);

      return true;
    } catch (Exception e) {
      throw new CustomException(GlobalExceptionCode.USER_INFO_UPDATE_FAILED);
    }
  }

  @Transactional
  @ActiveUserRequired
  public UserInfoResponse getUserInfo(Long userId) {
    User user = userRepository.findById(userId)
      .orElseThrow(() -> new CustomException(GlobalExceptionCode.USER_NOT_EXISTS));

    return UserInfoResponse.of(user);
  }

  @Transactional
  @ActiveUserRequired
  public Boolean withdrawUser(Long userId) {
    User user = userRepository.findById(userId)
      .orElseThrow(() -> new CustomException(GlobalExceptionCode.USER_NOT_EXISTS));

    user.updateUserStatus(Status.INACTIVE);
    return true;
  }

  @Transactional
  public Boolean updateUserPassword() {
    return false;
  }

  public String generateRandomNickname() {
    String nickname;

    do {
      String adjective = NicknameAdjective.random()
        .getValue();

      String middle = NicknameWord.random()
        .getValue();

      String suffix = generateRandomSuffix();

      nickname = String.format("%s %s %s", adjective, middle, suffix);

    } while (userRepository.existsByNickname(nickname));

    return nickname;
  }

  private String generateRandomSuffix() {
    StringBuilder sb = new StringBuilder(RANDOM_SUFFIX_LENGTH);
    for (int i = 0; i < RANDOM_SUFFIX_LENGTH; i++) {
      sb.append(RANDOM_CHARS.charAt(RANDOM.nextInt(RANDOM_CHARS.length())));
    }
    return sb.toString();
  }

  private void ensureIdentityUpdateAllowed(User user, boolean wantsNicknameChange, boolean wantsServerNameChange) {
    if (user.isVerified() && (wantsNicknameChange || wantsServerNameChange)) {
      throw new CustomException(GlobalExceptionCode.USER_VERIFICATION_REQUIRED_FOR_IDENTITY_UPDATE);
    }
  }

  private String resolveNextNickname(String requestedNickname, String currentNickname) {
    if (requestedNickname == null || requestedNickname.isBlank()) {
      return currentNickname;
    }
    return requestedNickname.trim();
  }

  private void validateNickname(String nickname) {
    if (!NICKNAME_PATTERN.matcher(nickname)
      .matches()) {
      throw new CustomException(GlobalExceptionCode.INVALID_NICKNAME_FORMAT);
    }
  }

  private void validateNicknameCombination(String nickname) {
    String[] parts = nickname.split(" ");
    if (parts.length != 3) {
      throw new CustomException(GlobalExceptionCode.INVALID_NICKNAME_COMBINATION);
    }

    String adjective = parts[0];
    String middle = parts[1];
    // suffix is random, so we don't validate it strictly against a list, but length check is implicit in pattern or logic

    if (!NicknameAdjective.contains(adjective)) {
      throw new CustomException(GlobalExceptionCode.INVALID_NICKNAME_COMBINATION);
    }

    if (!NicknameWord.contains(middle)) {
      throw new CustomException(GlobalExceptionCode.INVALID_NICKNAME_COMBINATION);
    }
  }

  private void ensureNicknameAvailable(String nickname, Long userId) {
    if (userRepository.existsByNicknameAndIdNot(nickname, userId)) {
      throw new CustomException(GlobalExceptionCode.NICKNAME_ALREADY_EXISTS);
    }
  }

}
