CREATE TABLE hero_settings (id INT PRIMARY KEY, created_at DATETIME(6) NOT NULL);
INSERT INTO hero_settings VALUES (1, UTC_TIMESTAMP(6)), (2, UTC_TIMESTAMP(6));
CREATE TABLE hero_slide (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, image_url VARCHAR(2048) NOT NULL,
 alt_text VARCHAR(200) NOT NULL, link_url VARCHAR(2048), position INT NOT NULL,
 starts_at DATETIME(6), ends_at DATETIME(6), deleted_at DATETIME(6),
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL
);
INSERT INTO hero_slide(image_url,alt_text,position,created_at,updated_at) VALUES
('/home-hero-library-v2.png','메모노기 도서관',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
('/home-hero-winter.png','메모노기 겨울 풍경',2,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));
CREATE TABLE hero_creative (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
 image_url VARCHAR(2048) NOT NULL, alt_text VARCHAR(200) NOT NULL, link_url VARCHAR(2048),
 blocked BOOLEAN NOT NULL DEFAULT FALSE, deleted_at DATETIME(6), created_at DATETIME(6) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE TABLE hero_week (
 week_start DATE PRIMARY KEY, opens_at DATETIME(6) NOT NULL, closes_at DATETIME(6) NOT NULL,
 display_starts_at DATETIME(6) NOT NULL, display_ends_at DATETIME(6) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
);
CREATE TABLE hero_auction (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, week_start DATE NOT NULL, slot INT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'OPEN', highest_bid BIGINT NOT NULL DEFAULT 0,
 highest_bidder_id BIGINT, highest_bid_id BIGINT, winning_bid_id BIGINT, reason VARCHAR(500),
 UNIQUE(week_start,slot), FOREIGN KEY(week_start) REFERENCES hero_week(week_start),
 CHECK(slot IN (1,2)), CHECK(highest_bid >= 0)
);
CREATE TABLE hero_bid (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, auction_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 creative_id BIGINT NOT NULL, amount BIGINT NOT NULL, status VARCHAR(20) NOT NULL,
 reason VARCHAR(500), idempotency_key VARCHAR(100) NOT NULL, created_at DATETIME(6) NOT NULL,
 UNIQUE(user_id,idempotency_key), FOREIGN KEY(auction_id) REFERENCES hero_auction(id),
 FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(creative_id) REFERENCES hero_creative(id),
 CHECK(amount >= 10)
);
CREATE TABLE hero_reservation (
 auction_id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, amount BIGINT NOT NULL,
 FOREIGN KEY(auction_id) REFERENCES hero_auction(id), FOREIGN KEY(user_id) REFERENCES users(id),
 CHECK(amount >= 10), INDEX hero_reservation_user(user_id)
);
CREATE TABLE hero_charge (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, auction_id BIGINT NOT NULL,
 kind VARCHAR(10) NOT NULL, amount BIGINT NOT NULL, created_at DATETIME(6) NOT NULL,
 UNIQUE(auction_id,kind), FOREIGN KEY(auction_id) REFERENCES hero_auction(id),
 FOREIGN KEY(user_id) REFERENCES users(id), INDEX hero_charge_user(user_id),
 CHECK((kind='CHARGE' AND amount>0) OR (kind='REFUND' AND amount<0))
);
CREATE TABLE hero_placement (
 auction_id BIGINT PRIMARY KEY, bid_id BIGINT NOT NULL, slot INT NOT NULL,
 starts_at DATETIME(6) NOT NULL, ends_at DATETIME(6) NOT NULL, stopped_at DATETIME(6),
 FOREIGN KEY(auction_id) REFERENCES hero_auction(id), FOREIGN KEY(bid_id) REFERENCES hero_bid(id)
);
CREATE TABLE hero_audit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, actor_id BIGINT NOT NULL, action VARCHAR(40) NOT NULL,
 target_id BIGINT, detail TEXT NOT NULL, created_at DATETIME(6) NOT NULL
);
