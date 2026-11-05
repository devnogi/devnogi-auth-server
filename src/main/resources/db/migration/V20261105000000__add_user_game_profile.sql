CREATE TABLE user_game_profile (
    user_id BIGINT PRIMARY KEY COMMENT '회원 ID',
    main_channel INT NULL COMMENT '주 채널 (1~42)',
    guild_name VARCHAR(30) NULL COMMENT '길드명',
    race VARCHAR(20) NULL COMMENT '종족 코드',
    arcana VARCHAR(32) NULL COMMENT '아르카나 코드',
    preferred_color_1 VARCHAR(11) NULL COMMENT '첫 번째 RGB 지향색',
    preferred_color_2 VARCHAR(11) NULL COMMENT '두 번째 RGB 지향색',
    mbti CHAR(4) NULL COMMENT 'MBTI 코드',
    bio VARCHAR(50) NULL COMMENT '자기소개',
    CONSTRAINT chk_user_game_profile_main_channel
        CHECK (main_channel IS NULL OR main_channel BETWEEN 1 AND 42),
    CONSTRAINT fk_user_game_profile_user_id
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) COMMENT='회원 공개 게임 프로필';
