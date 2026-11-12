package until.the.eternity.das.hero;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import until.the.eternity.das.common.response.CommonResponse;

@Component
@RequiredArgsConstructor
public class HeroMutationFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean mutation =
                !"GET".equals(request.getMethod())
                        && !"HEAD".equals(request.getMethod())
                        && !"OPTIONS".equals(request.getMethod());
        if (mutation
                && (path.startsWith("/api/user/hero/") || path.startsWith("/api/admin/hero/"))
                && (!"1".equals(request.getHeader("X-Hero-Action"))
                        || "cross-site".equals(request.getHeader("Sec-Fetch-Site")))) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            mapper.writeValue(
                    response.getWriter(), CommonResponse.error("HERO_CSRF", "허용되지 않은 변경 요청입니다."));
            return;
        }
        chain.doFilter(request, response);
    }
}
