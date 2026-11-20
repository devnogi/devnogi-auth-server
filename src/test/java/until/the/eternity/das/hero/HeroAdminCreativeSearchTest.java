package until.the.eternity.das.hero;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class HeroAdminCreativeSearchTest {
    @Test
    void validatesPaginationQueryAndStatusBeforeQueryingDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var search = new HeroAdminCreativeSearch(jdbc, Clock.systemUTC());
        assertThatThrownBy(() -> search.search(-1, 20, "", "ALL"))
                .isInstanceOf(HeroException.class);
        assertThatThrownBy(() -> search.search(0, 0, "", "ALL")).isInstanceOf(HeroException.class);
        assertThatThrownBy(() -> search.search(0, 51, "", "ALL")).isInstanceOf(HeroException.class);
        assertThatThrownBy(() -> search.search(0, 20, "x".repeat(101), "ALL"))
                .isInstanceOf(HeroException.class);
        assertThatThrownBy(() -> search.search(0, 20, "", "UNKNOWN"))
                .isInstanceOf(HeroException.class);
        verifyNoInteractions(jdbc);
    }
}
