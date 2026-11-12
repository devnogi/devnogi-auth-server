package until.the.eternity.das.hero;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import until.the.eternity.das.common.config.SecurityConfig;
import until.the.eternity.das.common.constant.JwtConstant;
import until.the.eternity.das.common.filter.UserAuthenticationFilter;
import until.the.eternity.das.common.util.JwtUtil;
import until.the.eternity.das.oauth.handler.OAuth2SuccessHandler;
import until.the.eternity.das.oauth.service.CustomOAuth2UserService;
import until.the.eternity.das.user.entity.UserRepository;

@WebMvcTest(HeroController.class)
@Import({SecurityConfig.class, UserAuthenticationFilter.class, HeroMutationFilter.class})
class HeroControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean HeroContentService content;
    @MockitoBean HeroAuctionService auctions;
    @MockitoBean Clock clock;
    @MockitoBean JwtUtil jwt;
    @MockitoBean JwtConstant constants;
    @MockitoBean UserRepository users;
    @MockitoBean CustomOAuth2UserService oauth;
    @MockitoBean OAuth2SuccessHandler success;

    @Test
    @WithMockUser(roles = "USER")
    void bidAmountsCannotBeCoercedFromDecimalOrString() throws Exception {
        for (String amount : List.of("10.5", "\"10\"", "9223372036854775808")) {
            mvc.perform(
                            post("/api/user/hero/auctions/1/bids")
                                    .header("X-Hero-Action", "1")
                                    .contentType("application/json")
                                    .content(
                                            "{\"creativeId\":1,\"amount\":"
                                                    + amount
                                                    + ",\"idempotencyKey\":\"request-123\"}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(auctions);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void malformedInputsAreBadRequests() throws Exception {
        mvc.perform(get("/api/admin/hero/preview").param("at", ""))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/hero/preview").param("at", "tomorrow"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/hero/preview")).andExpect(status().isBadRequest());
        mvc.perform(
                        put("/api/admin/hero/slides/order")
                                .header("X-Hero-Action", "1")
                                .contentType("application/json")
                                .content("{oops"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicHeroIsAnonymousAndUncached() throws Exception {
        Instant now = Instant.parse("2026-11-12T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        when(content.display(now))
                .thenReturn(
                        new HeroModels.Display(List.of(HeroContentService.fallback(1)), null, now));
        mvc.perform(get("/api/hero"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.items[0].position").value(1));
    }

    @Test
    void anonymousCannotReadPersonalOrAdmin() throws Exception {
        mvc.perform(get("/api/user/hero/creatives")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/hero/slides")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCannotUseAdmin() throws Exception {
        mvc.perform(get("/api/admin/hero/slides")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadSlides() throws Exception {
        when(content.slides()).thenReturn(List.of());
        mvc.perform(get("/api/admin/hero/slides")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void mutationsRejectFormCsrfAndCrossSite() throws Exception {
        mvc.perform(delete("/api/admin/hero/slides/1")).andExpect(status().isForbidden());
        mvc.perform(
                        delete("/api/admin/hero/slides/1")
                                .header("X-Hero-Action", "1")
                                .header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(content);
    }
}
