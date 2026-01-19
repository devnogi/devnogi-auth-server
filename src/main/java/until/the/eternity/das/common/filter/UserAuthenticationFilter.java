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

    String token = extractTokenFromCookie(request);

    if (token != null) {
      try {
        jwtUtil.validateToken(token);

        Long userId = jwtUtil.getUserIdFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);

        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
        List<GrantedAuthority> authorities = Collections.singletonList(authority);

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
