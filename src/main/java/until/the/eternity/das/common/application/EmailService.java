package until.the.eternity.das.common.application;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

  private final JavaMailSender javaMailSender;

  @Async
  public void sendVerificationEmail(String toEmail, String token) {
    String title = " DEVNOGI 회원가입 이메일 인증";
    String verificationLink = "https://devnogi-auth.shop/api/auth/verify-email?token=" + token;

    String content = String.format("""
      <div style="font-family: 'Apple SD Gothic Neo', 'sans-serif' !important; width: 540px; border-top: 4px solid #0058ff; margin: 100px auto; padding: 30px 0; box-sizing: border-box;">
          <h1 style="margin: 0; padding: 0 5px; font-size: 28px; font-weight: 400;">
              <span style="font-size: 15px; margin: 0 0 10px 3px;">DEVNOGI</span><br />
              <span style="color: #0058ff;">메일인증</span> 안내입니다.
          </h1>
          <p style="font-size: 16px; line-height: 26px; margin-top: 50px; padding: 0 5px;">
              DEVNOGI에 가입해 주셔서 감사합니다.<br />
              아래 버튼을 클릭하여 회원가입을 완료해 주세요.
          </p>
          <a style="color: #FFF; text-decoration: none; text-align: center;" href="%s" target="_blank">
              <p style="display: inline-block; width: 210px; height: 45px; margin: 30px 5px 40px; background: #0058ff; line-height: 45px; vertical-align: middle; font-size: 16px;">
                  메일 인증하기
              </p>
          </a>
      </div>
      """, verificationLink);

    try {
      MimeMessage mimeMessage = javaMailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
      helper.setTo(toEmail);
      helper.setSubject(title);
      helper.setText(content, true);
      javaMailSender.send(mimeMessage);
      log.info("인증 메일 발송 성공: {}", toEmail);
    } catch (MessagingException e) {
      log.error("메일 발송 실패", e);
      throw new CustomException(GlobalExceptionCode.EMAIL_SEND_FAIL);
    }
  }
}