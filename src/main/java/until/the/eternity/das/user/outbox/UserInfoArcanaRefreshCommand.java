package until.the.eternity.das.user.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Loads only the datasource and bounded replay service; never starts Flyway, Kafka or schedulers.
 */
@Slf4j
public final class UserInfoArcanaRefreshCommand {
    private UserInfoArcanaRefreshCommand() {}

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Configuration.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        try (var context = app.run(args)) {
            var env = context.getEnvironment();
            if (!env.getProperty("app.arcana-refresh.enabled", Boolean.class, false)) {
                throw new IllegalStateException(
                        "Explicit app.arcana-refresh.enabled=true required");
            }
            var refresh = context.getBean(UserInfoArcanaRefresh.class);
            String action = env.getProperty("app.arcana-refresh.action", "dry-run");
            int queued = 0;
            if ("run".equals(action)) {
                if (!"REFRESH_ARCANA_V1".equals(env.getProperty("app.arcana-refresh.confirm"))) {
                    throw new IllegalArgumentException(
                            "Confirm REFRESH_ARCANA_V1 after all old publishers drain");
                }
                queued =
                        refresh.refresh(
                                env.getProperty(
                                        "app.arcana-refresh.batch-size", Integer.class, 100));
            } else if (!"dry-run".equals(action))
                throw new IllegalArgumentException("Unknown refresh action");
            log.info(
                    "Arcana refresh action={} queued={} remaining={}",
                    action,
                    queued,
                    refresh.remaining());
        }
    }

    @ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        TransactionAutoConfiguration.class
    })
    @EnableTransactionManagement
    @Import(UserInfoArcanaRefresh.class)
    static class Configuration {}
}
