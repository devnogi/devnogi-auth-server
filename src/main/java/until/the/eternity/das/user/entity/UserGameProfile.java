package until.the.eternity.das.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import until.the.eternity.das.user.application.GameProfileValues;
import until.the.eternity.das.user.entity.enums.Arcana;
import until.the.eternity.das.user.entity.enums.Mbti;
import until.the.eternity.das.user.entity.enums.Race;

@Entity
@Table(name = "user_game_profile")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserGameProfile {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "main_channel")
    private Integer mainChannel;

    @Column(name = "guild_name", length = 30)
    private String guildName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private Race race;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 32)
    private Arcana arcana;

    @Column(name = "preferred_color_1", length = 11)
    private String preferredColor1;

    @Column(name = "preferred_color_2", length = 11)
    private String preferredColor2;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 4)
    private Mbti mbti;

    @Column(length = 50)
    private String bio;

    public void replace(GameProfileValues values) {
        this.mainChannel = values.mainChannel();
        this.guildName = values.guildName();
        this.race = values.race();
        this.arcana = values.arcana();
        this.preferredColor1 = values.preferredColor1();
        this.preferredColor2 = values.preferredColor2();
        this.mbti = values.mbti();
        this.bio = values.bio();
    }
}
