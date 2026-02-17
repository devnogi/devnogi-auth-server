package until.the.eternity.das.user.application;

import jakarta.transaction.Transactional;
import java.util.Objects;
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
import until.the.eternity.das.user.entity.enums.Status;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final S3Service s3Service;
  private final KafkaProducerService kafkaProducerService;

  @Transactional
  @ActiveUserRequired
  public Boolean updateUserInfo(UserInfoUpdateRequest request, Long userId) {
    User user = userRepository.findById(userId)
      .orElseThrow(() -> new CustomException(GlobalExceptionCode.USER_NOT_EXISTS));

    boolean wantsNicknameChange =
      request.nickname() != null && !Objects.equals(request.nickname(), user.getNickname());
    ensureIdentityUpdateAllowed(user, wantsNicknameChange, false);

    String profileImageUrl = null;

    if (request.file() != null) {
      if (user.getProfileImageUrl() != null) {
        s3Service.deleteImage(user.getProfileImageUrl());
      }

      String dirName = "profile";
      profileImageUrl = s3Service.uploadImage(request.file(), dirName);
    }

    try {
      user.updateUserInfo(request.nickname(), profileImageUrl);

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

  private void ensureIdentityUpdateAllowed(User user, boolean wantsNicknameChange, boolean wantsServerNameChange) {
    if (user.isVerified() && (wantsNicknameChange || wantsServerNameChange)) {
      throw new CustomException(GlobalExceptionCode.USER_VERIFICATION_REQUIRED_FOR_IDENTITY_UPDATE);
    }
  }

}
