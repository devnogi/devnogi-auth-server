package until.the.eternity.das.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import until.the.eternity.das.common.constant.JwtConstant;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.util.JwtUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UserAuthenticationFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;
  private final JwtConstant jwtConstant;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
    throws ServletException, IOException {

    // 1. 쿠키에서 Access Token(JWT) 추출
    String token = extractTokenFromCookie(request);

    if (token != null) {
      try {
        // 2. JwtUtil을 사용해 토큰 유효성 검증
        //    (validateToken 내에서 만료, 형식 오류 등을 검증하고 예외를 던짐)
        jwtUtil.validateToken(token);

        // 3. 토큰이 유효하면, JwtUtil을 사용해 사용자 ID 추출
        Long userId = jwtUtil.getUserIdFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);

        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
        List<GrantedAuthority> authorities = Collections.singletonList(authority);

        // 4. 추출한 userId로 Spring Security 인증 객체 생성
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
          userId,
          null,
          authorities
        );

        SecurityContextHolder.getContext()
          .setAuthentication(authentication);

      } catch (CustomException e) {
        SecurityContextHolder.clearContext();
      }
    }

    // 다음 필터로 요청을 전달합니다.
    filterChain.doFilter(request, response);
  }

  /**
   * HttpServletRequest의 쿠키 배열에서 Access Token(JWT)을 추출하는 메서드
   *
   * @param request
   * @return JWT 문자열 또는 null
   */
  private String extractTokenFromCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }

    return Arrays.stream(cookies)
      .filter(cookie -> jwtConstant.getAccessTokenCookieName()
        .equals(cookie.getName()))
      .map(Cookie::getValue)
      .findFirst()
      .orElse(null);
  }
}
