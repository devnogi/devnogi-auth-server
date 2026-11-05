package until.the.eternity.das.user.application;

import until.the.eternity.das.user.entity.enums.Arcana;
import until.the.eternity.das.user.entity.enums.Mbti;
import until.the.eternity.das.user.entity.enums.Race;

public record GameProfileValues(
        Integer mainChannel,
        String guildName,
        Race race,
        Arcana arcana,
        String preferredColor1,
        String preferredColor2,
        Mbti mbti,
        String bio) {
    public boolean isEmpty() {
        return mainChannel == null
                && guildName == null
                && race == null
                && arcana == null
                && preferredColor1 == null
                && preferredColor2 == null
                && mbti == null
                && bio == null;
    }
}
