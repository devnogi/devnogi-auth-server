-- Existing duplicate unverified names must be reviewed before this migration.
-- The unique index deliberately fails rather than changing any existing nickname.
ALTER TABLE users
    ADD COLUMN unverified_nickname VARCHAR(50)
        GENERATED ALWAYS AS (CASE WHEN is_verified = FALSE THEN nickname ELSE NULL END) VIRTUAL;

ALTER TABLE users
    ADD UNIQUE INDEX uk_unverified_nickname (unverified_nickname);
