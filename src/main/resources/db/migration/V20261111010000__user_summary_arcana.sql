ALTER TABLE user_info_outbox
    ADD COLUMN arcana VARCHAR(32) NULL,
    ADD COLUMN arcana_refreshed BOOLEAN NOT NULL DEFAULT FALSE;

-- Queue the actual profile at deployment. The explicit post-drain refresh marker
-- stays false: an old publisher may acknowledge this revision without arcana.
UPDATE user_info_outbox o LEFT JOIN user_game_profile p ON p.user_id = o.user_id
SET o.arcana = p.arcana,
    o.pending_since = IF(o.published_revision >= o.revision, UTC_TIMESTAMP(6), o.pending_since),
    o.revision = o.revision + 1,
    o.attempt_count = 0,
    o.next_attempt_at = UTC_TIMESTAMP(6),
    o.updated_at = UTC_TIMESTAMP(6);
