package until.the.eternity.das.experience;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ActivityEvent(
        String eventId,
        int schemaVersion,
        String activityKey,
        String activityType,
        long actorUserId,
        long authorUserId,
        String targetType,
        long targetId,
        String rootTargetType,
        long rootTargetId,
        boolean active,
        long sourceRevision,
        Instant occurredAt) {
    public void validate() {
        UUID.fromString(eventId);
        if (schemaVersion != 1
                || actorUserId <= 0
                || authorUserId <= 0
                || targetId <= 0
                || rootTargetId <= 0
                || sourceRevision < 0
                || occurredAt == null) {
            throw new IllegalArgumentException("Invalid activity event");
        }
        Set<String> allowedTargets =
                switch (activityType) {
                    case "POST_PUBLISHED" -> Set.of("POST");
                    case "COMMENT_CREATED" -> Set.of("COMMENT", "OUTFIT_COMMENT");
                    case "OUTFIT_PUBLISHED" -> Set.of("OUTFIT");
                    case "GUILD_POST_PUBLISHED" -> Set.of("GUILD_POST", "GUILD_RECRUITMENT");
                    case "GUILD_REGISTERED" -> Set.of("GUILD");
                    case "LIKE" -> Set.of("POST", "COMMENT", "OUTFIT");
                    default -> throw new IllegalArgumentException("Unknown activity type");
                };
        String expectedKey =
                targetType
                        + ":"
                        + targetId
                        + ("LIKE".equals(activityType) ? ":LIKE:" + actorUserId : ":CREATE");
        if (!allowedTargets.contains(targetType)
                || !expectedKey.equals(activityKey)
                || (!"LIKE".equals(activityType) && actorUserId != authorUserId)) {
            throw new IllegalArgumentException("Invalid activity identity");
        }
        String expectedRoot =
                switch (targetType) {
                    case "COMMENT" -> "POST";
                    case "OUTFIT_COMMENT" -> "OUTFIT";
                    default -> targetType;
                };
        if (!expectedRoot.equals(rootTargetType)
                || (expectedRoot.equals(targetType) && rootTargetId != targetId)) {
            throw new IllegalArgumentException("Invalid activity root");
        }
    }
}
