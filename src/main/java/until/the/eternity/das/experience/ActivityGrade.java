package until.the.eternity.das.experience;

import java.util.Arrays;
import java.util.List;

public enum ActivityGrade {
    APPRENTICE("견습", 0),
    SENIOR("수석", 45),
    EXPERT("달인", 250),
    CHAMPION("챔피언", 1000),
    MASTER("마스터", 2500),
    GRANDMASTER("그랜드마스터", 5000);

    private final String label;
    private final long minXp;

    ActivityGrade(String label, long minXp) {
        this.label = label;
        this.minXp = minXp;
    }

    public long minXp() {
        return minXp;
    }

    public ActivityGrade next() {
        return ordinal() + 1 == values().length ? null : values()[ordinal() + 1];
    }

    public static ActivityGrade fromXp(long xp) {
        if (xp < 0) throw new IllegalArgumentException("Experience cannot be negative");
        ActivityGrade result = APPRENTICE;
        for (ActivityGrade grade : values()) if (xp >= grade.minXp) result = grade;
        return result;
    }

    public static List<Threshold> thresholds() {
        return Arrays.stream(values()).map(g -> new Threshold(g, g.label, g.minXp)).toList();
    }

    public record Threshold(ActivityGrade code, String label, long minXp) {}
}
