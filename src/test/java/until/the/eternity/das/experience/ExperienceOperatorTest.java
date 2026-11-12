package until.the.eternity.das.experience;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

class ExperienceOperatorTest {
    @Test
    void freezeRequiresExactGuardBeforeTouchingDatabase() {
        ExperienceOperations operations = mock(ExperienceOperations.class);
        var operator =
                new ExperienceOperator(
                        operations, mock(ConfigurableApplicationContext.class), "freeze", "");
        assertThatThrownBy(() -> operator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(operations);
    }

    @Test
    void failedReconciliationFailsTheCommand() {
        ExperienceOperations operations = mock(ExperienceOperations.class);
        when(operations.reconcile())
                .thenReturn(Map.of("ledger_mismatches", 1L, "grade_mismatches", 0L));
        var operator =
                new ExperienceOperator(
                        operations, mock(ConfigurableApplicationContext.class), "reconcile", "");
        assertThatThrownBy(() -> operator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void advertisingMismatchAlsoBlocksActivation() {
        ExperienceOperations operations = mock(ExperienceOperations.class);
        when(operations.reconcile())
                .thenReturn(
                        Map.of(
                                "ledger_mismatches",
                                0L,
                                "grade_mismatches",
                                0L,
                                "reservation_mismatches",
                                1L));
        var operator =
                new ExperienceOperator(
                        operations, mock(ConfigurableApplicationContext.class), "reconcile", "");
        assertThatThrownBy(() -> operator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
    }
}
