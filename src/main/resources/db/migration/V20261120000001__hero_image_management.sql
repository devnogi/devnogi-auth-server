ALTER TABLE hero_slide ADD COLUMN visible BOOLEAN NOT NULL DEFAULT TRUE;

-- Only the original, untouched library seed is eligible. Customized/deleted records are preserved.
SET @hero_default_banner_id = (
    SELECT id FROM hero_slide
    WHERE image_url = '/home-hero-library-v2.png'
      AND alt_text = '메모노기 도서관'
      AND link_url IS NULL AND starts_at IS NULL AND ends_at IS NULL AND deleted_at IS NULL
    ORDER BY id LIMIT 1
);
SET @hero_banner_exists = (
    SELECT COUNT(*) FROM hero_slide WHERE image_url = '/home-hero-auction.jpg' AND deleted_at IS NULL
);

UPDATE hero_slide
SET position = position + 1, updated_at = UTC_TIMESTAMP(6)
WHERE deleted_at IS NULL AND id <> @hero_default_banner_id
  AND @hero_default_banner_id IS NOT NULL AND @hero_banner_exists = 0;

UPDATE hero_slide
SET image_url = '/home-hero-auction.jpg',
    alt_text = '메모노기 광고 경매 안내 — 활동으로 모은 XP로 메인 화면에 광고하세요.',
    link_url = 'https://www.memonogi.com/hero-auctions', position = 1,
    updated_at = UTC_TIMESTAMP(6)
WHERE id = @hero_default_banner_id AND @hero_banner_exists = 0;
