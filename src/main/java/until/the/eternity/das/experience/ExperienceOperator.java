package until.the.eternity.das.experience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.experience.operator-enabled", havingValue = "true")
public class ExperienceOperator implements ApplicationRunner {
    private final ExperienceOperations operations;
    private final ConfigurableApplicationContext context;
    private final String action;
    private final String confirmation;

    public ExperienceOperator(
            ExperienceOperations operations,
            ConfigurableApplicationContext context,
            @Value("${app.experience.operator-action:dry-run}") String action,
            @Value("${app.experience.operator-confirm:}") String confirmation) {
        this.operations = operations;
        this.context = context;
        this.action = action;
        this.confirmation = confirmation;
    }

    @Override
    public void run(ApplicationArguments args) {
        Object result =
                switch (action) {
                    case "dry-run" -> operations.dryRun();
                    case "reconcile" -> operations.reconcile();
                    case "freeze" -> {
                        if (!"FREEZE_XP_V1".equals(confirmation))
                            throw new IllegalArgumentException(
                                    "Freeze requires explicit FREEZE_XP_V1 confirmation");
                        yield operations.freeze();
                    }
                    default ->
                            throw new IllegalArgumentException(
                                    "Unknown experience operator action");
                };
        log.info("Experience operator action={} result={}", action, result);
        if ("reconcile".equals(action)
                && result instanceof java.util.Map<?, ?> values
                && values.entrySet().stream()
                        .anyMatch(
                                entry ->
                                        (entry.getKey().toString().endsWith("mismatches")
                                                        || "negative_advertising_balances"
                                                                .equals(entry.getKey()))
                                                && ((Number) entry.getValue()).longValue() != 0)) {
            throw new IllegalStateException(
                    "Experience reconciliation failed; do not expose the feature");
        }
        SpringApplication.exit(context, () -> 0);
    }
}
