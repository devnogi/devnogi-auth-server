package until.the.eternity.das.search.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import until.the.eternity.das.search.application.UserSearchService;
import until.the.eternity.das.search.dto.UserSearchResponse;

@ExtendWith(MockitoExtension.class)
class UserSearchControllerTest {

    @Mock private UserSearchService userSearchService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc =
                MockMvcBuilders.standaloneSetup(new UserSearchController(userSearchService))
                        .setValidator(validator)
                        .build();
    }

    @Test
    void returnsOnlyThePublicSearchFields() throws Exception {
        when(userSearchService.search("메모", 10))
                .thenReturn(List.of(new UserSearchResponse("USER", 7L, "메모노기", "류트")));

        mockMvc.perform(get("/api/user/search").param("query", "메모"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("USER"))
                .andExpect(jsonPath("$.data[0].userId").value(7))
                .andExpect(jsonPath("$.data[0].nickname").value("메모노기"))
                .andExpect(jsonPath("$.data[0].serverName").value("류트"))
                .andExpect(jsonPath("$.data[0].email").doesNotExist())
                .andExpect(jsonPath("$.data[0].lastLoginAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].role").doesNotExist());
    }

    @Test
    void rejectsInvalidQueriesAndLimitsAboveTen() throws Exception {
        mockMvc.perform(get("/api/user/search")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/user/search").param("query", " "))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/user/search").param("query", "x".repeat(51)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/user/search").param("query", "메모").param("limit", "11"))
                .andExpect(status().isBadRequest());
    }
}
