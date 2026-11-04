package until.the.eternity.das.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UserSearchMigrationContractTest {

    @Test
    void addsADescendingLastLoginIndexWithoutChangingExistingMigrations() throws Exception {
        try (var stream =
                getClass()
                        .getResourceAsStream(
                                "/db/migration/V20261104000000__add_users_last_login_index.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql)
                    .contains("CREATE INDEX idx_users_last_login_at_desc")
                    .contains("last_login_at DESC, id DESC");
        }
    }
}
