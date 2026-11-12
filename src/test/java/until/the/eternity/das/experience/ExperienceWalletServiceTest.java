package until.the.eternity.das.experience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExperienceWalletServiceTest {
    @Test
    void reservationsReduceSpendingPowerButDoNotReduceGrade() {
        var balance = ExperienceWalletService.balance(45, 0, 30);
        assertThat(balance.currentXp()).isEqualTo(45);
        assertThat(balance.availableXp()).isEqualTo(15);
        assertThat(ActivityGrade.fromXp(balance.currentXp())).isEqualTo(ActivityGrade.SENIOR);
    }

    @Test
    void settlementReducesGradeWithoutChangingActivityLedger() {
        var balance = ExperienceWalletService.balance(45, 10, 0);
        assertThat(balance.activityXp()).isEqualTo(45);
        assertThat(balance.currentXp()).isEqualTo(35);
        assertThat(ActivityGrade.fromXp(balance.currentXp())).isEqualTo(ActivityGrade.APPRENTICE);
    }

    @Test
    void reversalPreservesDebtAndFutureActivityRepaysIt() {
        var afterReversal = ExperienceWalletService.balance(5, 40, 10);
        assertThat(afterReversal.currentXp()).isZero();
        assertThat(afterReversal.availableXp()).isZero();
        assertThat(afterReversal.deficitXp()).isEqualTo(35);
        assertThat(ExperienceWalletService.balance(30, 40, 0).deficitXp()).isEqualTo(10);
        assertThat(ExperienceWalletService.balance(50, 40, 0).availableXp()).isEqualTo(10);
    }

    @Test
    void refundRestoresCurrentXpAndUnderfundedReservationsNeverExposeNegativeAvailableXp() {
        assertThat(ExperienceWalletService.balance(45, 0, 0).currentXp()).isEqualTo(45);
        assertThat(ExperienceWalletService.balance(45, 0, 60).availableXp()).isZero();
        assertThatThrownBy(() -> ExperienceWalletService.balance(45, -1, 0))
                .isInstanceOf(IllegalStateException.class);
    }
}
