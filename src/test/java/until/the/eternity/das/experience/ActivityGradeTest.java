package until.the.eternity.das.experience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ActivityGradeTest {
    @Test
    void thresholdsAndRequestedActivityExamples() {
        assertThat(ActivityGrade.fromXp(20 + 5 * 5)).isEqualTo(ActivityGrade.SENIOR);
        assertThat(ActivityGrade.fromXp(10 * 20 + 10 * 5)).isEqualTo(ActivityGrade.EXPERT);
        assertThat(ActivityGrade.fromXp(200 * 20 + 200 * 5)).isEqualTo(ActivityGrade.GRANDMASTER);
        assertThat(ActivityGrade.fromXp(100 * 20 + 100 * 20 + 200 * 5))
                .isEqualTo(ActivityGrade.GRANDMASTER);
        ActivityGrade previous = ActivityGrade.APPRENTICE;
        for (ActivityGrade grade : ActivityGrade.values()) {
            assertThat(ActivityGrade.fromXp(grade.minXp())).isEqualTo(grade);
            assertThat(ActivityGrade.fromXp(grade.minXp() + 1)).isEqualTo(grade);
            if (grade.minXp() > 0)
                assertThat(ActivityGrade.fromXp(grade.minXp() - 1)).isEqualTo(previous);
            previous = grade;
        }
        assertThat(ActivityGrade.GRANDMASTER.next()).isNull();
        assertThatThrownBy(() -> ActivityGrade.fromXp(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
