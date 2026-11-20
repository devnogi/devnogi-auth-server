package until.the.eternity.das.hero;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
    @MockitoBean HeroAdminCreativeSearch creativeSearch;
    @MockitoBean Clock clock;
    @MockitoBean JwtUtil jwt;
    @MockitoBean JwtConstant constants;
    @MockitoBean UserRepository users;
    @MockitoBean CustomOAuth2UserService oauth;
    @MockitoBean OAuth2SuccessHandler success;

    @Test
    void publicAuctionsSerializeScheduledKoreanStartAsUtc() throws Exception {
        var response =
                new HeroModels.Auctions(
                        List.of(),
                        Instant.parse("2026-11-12T00:00:00Z"),
                        false,
                        Instant.parse("2026-11-15T15:00:00Z"));
        when(auctions.current()).thenReturn(response);
        mvc.perform(get("/api/hero/auctions/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.scheduledStartAt").value("2026-11-15T15:00:00Z"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminAuctionsSerializeScheduledStartAndNullAfterOpening() throws Exception {
        when(auctions.all())
                .thenReturn(
                        new HeroModels.Auctions(
                                List.of(),
                                Instant.parse("2026-11-12T00:00:00Z"),
                                false,
                                Instant.parse("2026-11-15T15:00:00Z")));
        mvc.perform(get("/api/admin/hero/auctions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduledStartAt").value("2026-11-15T15:00:00Z"));
        when(auctions.all())
                .thenReturn(
                        new HeroModels.Auctions(
                                List.of(), Instant.parse("2026-11-15T15:00:00Z"), true, null));
        mvc.perform(get("/api/admin/hero/auctions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(
                        jsonPath("$.data.scheduledStartAt")
                                .value(org.hamcrest.Matchers.nullValue()));
    }

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

    @Test
    @WithMockUser(roles = "ADMIN")
    void visibilityRejectsOmittedNullAndCoercedBooleans() throws Exception {
        for (String body :
                List.of("{}", "{\"visible\":null}", "{\"visible\":\"false\"}", "{\"visible\":0}")) {
            mvc.perform(
                            put("/api/admin/hero/slides/1/visibility")
                                    .header("X-Hero-Action", "1")
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(content);
    }

    @Test
    void adminAndSuperAdminCanManageVisibilityAndUnblock() throws Exception {
        for (String role : List.of("ADMIN", "SUPER_ADMIN")) {
            var auth =
                    new UsernamePasswordAuthenticationToken(
                            7L, "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            mvc.perform(
                            put("/api/admin/hero/slides/1/visibility")
                                    .with(authentication(auth))
                                    .header("X-Hero-Action", "1")
                                    .contentType("application/json")
                                    .content("{\"visible\":false}"))
                    .andExpect(status().isOk());
            mvc.perform(
                            post("/api/admin/hero/creatives/2/unblock")
                                    .with(authentication(auth))
                                    .header("X-Hero-Action", "1")
                                    .contentType("application/json")
                                    .content("{\"reason\":\"Reviewed\"}"))
                    .andExpect(status().isOk());
        }
        verify(content, times(2)).visibility(7, 1, false);
        verify(auctions, times(2)).unblock(7, 2, "Reviewed");
    }

    @Test
    void newAdminEndpointsRejectAnonymousAndMemberAccess() throws Exception {
        mvc.perform(get("/api/admin/hero/creatives/search")).andExpect(status().isUnauthorized());
        var auth =
                new UsernamePasswordAuthenticationToken(
                        7L, "unused", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        mvc.perform(get("/api/admin/hero/creatives/search").with(authentication(auth)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put("/api/admin/hero/slides/1/visibility")
                                .with(authentication(auth))
                                .header("X-Hero-Action", "1")
                                .contentType("application/json")
                                .content("{\"visible\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/admin/hero/creatives/2/unblock")
                                .with(authentication(auth))
                                .header("X-Hero-Action", "1")
                                .contentType("application/json")
                                .content("{\"reason\":\"Reviewed\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(content, auctions, creativeSearch);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void searchSerializesAdminMetadataWithoutChangingLegacyCreativeShape() throws Exception {
        Instant createdAt = Instant.parse("2026-11-20T00:00:00Z");
        var item =
                new HeroModels.AdminHeroCreative(
                        2,
                        "/image.jpg",
                        "Example",
                        null,
                        true,
                        createdAt,
                        7,
                        "Synthetic owner",
                        "Policy",
                        0,
                        0,
                        0);
        when(creativeSearch.search(1, 20, "Example", "BLOCKED"))
                .thenReturn(new HeroModels.AdminCreativePage(List.of(item), 1, 20, 21, 2));
        mvc.perform(
                        get("/api/admin/hero/creatives/search")
                                .param("page", "1")
                                .param("query", "Example")
                                .param("status", "BLOCKED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(21))
                .andExpect(jsonPath("$.data.items[0].userId").value(7))
                .andExpect(jsonPath("$.data.items[0].blockReason").value("Policy"));
        when(content.creatives(null))
                .thenReturn(
                        List.of(
                                new HeroModels.Creative(
                                        2, "/image.jpg", "Example", null, true, createdAt)));
        mvc.perform(get("/api/admin/hero/creatives"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void newMutationsRequireExistingSameOriginProtection() throws Exception {
        mvc.perform(
                        put("/api/admin/hero/slides/1/visibility")
                                .contentType("application/json")
                                .content("{\"visible\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/admin/hero/creatives/2/unblock")
                                .header("X-Hero-Action", "1")
                                .header("Sec-Fetch-Site", "cross-site")
                                .contentType("application/json")
                                .content("{\"reason\":\"Reviewed\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(content, auctions);
    }
}
