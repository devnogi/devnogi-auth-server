package until.the.eternity.das.oauth.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import until.the.eternity.das.auth.application.AuthConverter;
import until.the.eternity.das.auth.dto.request.SocialSignUpRequest;
import until.the.eternity.das.auth.dto.response.SignUpResponse;
import until.the.eternity.das.common.application.S3Service;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.oauth.dto.OauthUserDTO;
import until.the.eternity.das.oauth.entity.OauthUser;
import until.the.eternity.das.oauth.entity.OauthUserRepository;
import until.the.eternity.das.role.entity.Role;
import until.the.eternity.das.role.entity.RoleRepository;
import until.the.eternity.das.role.entity.enums.Name;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialAuthService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final OauthUserRepository oauthUserRepository;
  private final AuthConverter authConverter;
  private final S3Service s3Service;

  @Transactional
  public SignUpResponse completeSocialSignup(SocialSignUpRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new CustomException(GlobalExceptionCode.EMAIL_ALREADY_EXISTS);
    }

    OauthUserDTO dto = new OauthUserDTO(request.providerUserId(), request.email(), request.provider());

    Role userRole = roleRepository.findByName(Name.USER)
      .orElseThrow(() -> new CustomException(GlobalExceptionCode.USER_ROLE_NOT_EXISTS));

    // 프로필 이미지 등록
    String dirName = "profile";
    String profileImageUrl = s3Service.uploadImage(request.file(), dirName);

    User user = authConverter.fromOauthUserDTOToUser(dto, request.nickname(), userRole, profileImageUrl);
    userRepository.save(user);

    OauthUser oauthUser = OauthUser.builder()
      .user(user)
      .provider(request.provider())
      .providerUserId(request.providerUserId())
      .linkedAt(java.time.LocalDateTime.now())
      .build();
    oauthUserRepository.save(oauthUser);

    return SignUpResponse.of(user.getId());
  }
}