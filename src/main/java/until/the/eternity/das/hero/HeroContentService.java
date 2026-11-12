package until.the.eternity.das.hero;

import static until.the.eternity.das.hero.HeroRepository.time;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import until.the.eternity.das.hero.HeroModels.*;

@Service
@RequiredArgsConstructor
public class HeroContentService {
    private final HeroRepository repo;
    private final HeroMedia media;
    private final Clock clock;

    public List<Slide> slides() {
        return repo.jdbc.query(
                "SELECT * FROM hero_slide WHERE deleted_at IS NULL ORDER BY position,id",
                HeroRepository::slide);
    }

    @Transactional
    public Slide createSlide(
            long actor, MultipartFile image, String alt, String link, Instant start, Instant end) {
        lockSettings();
        List<Slide> existing = slides();
        if (existing.size() >= 10) throw HeroException.conflict("기본 이미지는 최대 10개입니다.");
        alt = HeroValidation.text(alt);
        link = HeroValidation.link(link);
        HeroValidation.period(start, end);
        String imageUrl = media.upload(image);
        repo.jdbc.update(
                "INSERT INTO hero_slide(image_url,alt_text,link_url,position,starts_at,ends_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",
                imageUrl,
                alt,
                link,
                existing.stream().mapToInt(Slide::position).max().orElse(0) + 1,
                time(start),
                time(end),
                time(clock.instant()),
                time(clock.instant()));
        long id = repo.insertedId();
        audit(actor, "SLIDE_CREATE", id, alt);
        return slide(id);
    }

    @Transactional
    public Slide updateSlide(
            long actor,
            long id,
            MultipartFile image,
            String alt,
            String link,
            Instant start,
            Instant end) {
        lockSettings();
        Slide old = slide(id);
        alt = HeroValidation.text(alt);
        link = HeroValidation.link(link);
        HeroValidation.period(start, end);
        String url = image == null ? old.imageUrl() : media.upload(image);
        repo.jdbc.update(
                "UPDATE hero_slide SET image_url=?,alt_text=?,link_url=?,starts_at=?,ends_at=?,updated_at=? WHERE id=?",
                url,
                alt,
                link,
                time(start),
                time(end),
                time(clock.instant()),
                id);
        audit(actor, "SLIDE_UPDATE", id, "previous=" + old + ";next=" + slide(id));
        return slide(id);
    }

    @Transactional
    public void deleteSlide(long actor, long id) {
        lockSettings();
        slide(id);
        if (slides().size() <= 1) throw HeroException.conflict("마지막 기본 이미지는 삭제할 수 없습니다.");
        repo.jdbc.update(
                "UPDATE hero_slide SET deleted_at=?,updated_at=? WHERE id=?",
                time(clock.instant()),
                time(clock.instant()),
                id);
        audit(actor, "SLIDE_DELETE", id, "soft delete");
    }

    @Transactional
    public void reorder(long actor, List<Long> ids) {
        lockSettings();
        List<Long> existing = slides().stream().map(Slide::id).toList();
        if (ids == null
                || ids.size() != existing.size()
                || !new HashSet<>(ids).equals(new HashSet<>(existing)))
            throw HeroException.invalid("모든 기본 이미지의 순서를 지정해 주세요.");
        for (int i = 0; i < ids.size(); i++)
            repo.jdbc.update(
                    "UPDATE hero_slide SET position=?,updated_at=? WHERE id=?",
                    i + 1,
                    time(clock.instant()),
                    ids.get(i));
        audit(actor, "SLIDE_ORDER", null, ids.toString());
    }

    public Display display(Instant at) {
        List<Slide> defaults =
                slides().stream().filter(s -> active(at, s.startsAt(), s.endsAt())).toList();
        List<Slide> ads =
                repo.jdbc.query(
                        """
                SELECT p.auction_id AS id,c.image_url,c.alt_text,c.link_url,p.slot,p.starts_at,p.ends_at
                FROM hero_placement p JOIN hero_bid b ON b.id=p.bid_id JOIN hero_creative c ON c.id=b.creative_id
                WHERE p.stopped_at IS NULL AND c.blocked=FALSE AND p.starts_at<=? AND p.ends_at>?
                ORDER BY p.slot
                """,
                        (rs, row) ->
                                new Slide(
                                        rs.getLong("id"),
                                        rs.getString("image_url"),
                                        rs.getString("alt_text"),
                                        rs.getString("link_url"),
                                        rs.getInt("slot"),
                                        true,
                                        HeroRepository.instant(rs, "starts_at"),
                                        HeroRepository.instant(rs, "ends_at"),
                                        null),
                        time(at),
                        time(at));
        List<Slide> result = new ArrayList<>();
        int next = 0;
        for (int position = 1; position <= 10; position++) {
            int slot = position;
            Slide ad = ads.stream().filter(a -> a.position() == slot).findFirst().orElse(null);
            if (ad != null) {
                result.add(ad);
                continue;
            }
            if (next < defaults.size()) {
                Slide item = defaults.get(next++);
                result.add(
                        new Slide(
                                item.id(),
                                item.imageUrl(),
                                item.altText(),
                                item.linkUrl(),
                                position,
                                false,
                                item.startsAt(),
                                item.endsAt(),
                                null));
            } else if (result.isEmpty()
                    || (position == 1 && ads.stream().anyMatch(a -> a.position() == 2))) {
                result.add(fallback(position));
            } else if (position >= 2) break;
        }
        List<Instant> boundaries = new ArrayList<>();
        for (Slide s : slides()) {
            if (s.startsAt() != null && s.startsAt().isAfter(at)) boundaries.add(s.startsAt());
            if (s.endsAt() != null && s.endsAt().isAfter(at)) boundaries.add(s.endsAt());
        }
        boundaries.addAll(
                repo.jdbc.query(
                        "SELECT starts_at AS boundary FROM hero_placement WHERE stopped_at IS NULL AND starts_at>? UNION ALL SELECT ends_at AS boundary FROM hero_placement WHERE stopped_at IS NULL AND ends_at>?",
                        (rs, row) -> HeroRepository.instant(rs, "boundary"),
                        time(at),
                        time(at)));
        return new Display(
                result, boundaries.stream().min(Instant::compareTo).orElse(null), clock.instant());
    }

    public static boolean active(Instant at, Instant start, Instant end) {
        return (start == null || !at.isBefore(start)) && (end == null || at.isBefore(end));
    }

    static Slide fallback(int position) {
        return new Slide(
                0, "/home-hero-library-v2.png", "메모노기", null, position, false, null, null, null);
    }

    @Transactional
    public Creative createCreative(long user, MultipartFile image, String alt, String link) {
        alt = HeroValidation.text(alt);
        link = HeroValidation.link(link);
        String url = media.upload(image);
        repo.jdbc.update(
                "INSERT INTO hero_creative(user_id,image_url,alt_text,link_url,created_at) VALUES(?,?,?,?,?)",
                user,
                url,
                alt,
                link,
                time(clock.instant()));
        return creative(repo.insertedId());
    }

    public List<Creative> creatives(Long user) {
        return user == null
                ? repo.jdbc.query(
                        """
                        SELECT c.* FROM hero_creative c WHERE c.deleted_at IS NULL AND (
                          c.id IN (SELECT recent.id FROM (
                            SELECT id FROM hero_creative WHERE deleted_at IS NULL ORDER BY id DESC LIMIT 100
                          ) recent)
                          OR EXISTS (SELECT 1 FROM hero_bid b LEFT JOIN hero_placement p ON p.bid_id=b.id
                            WHERE b.creative_id=c.id AND (b.status='LEADING'
                              OR (p.stopped_at IS NULL AND p.ends_at>?)))
                        ) ORDER BY c.id DESC
                        """,
                        HeroRepository::creative,
                        time(clock.instant()))
                : repo.jdbc.query(
                        "SELECT * FROM hero_creative WHERE user_id=? AND deleted_at IS NULL ORDER BY id DESC LIMIT 100",
                        HeroRepository::creative,
                        user);
    }

    @Transactional
    public void deleteCreative(long user, long id) {
        // Lock the creative also taken by bidding, preventing a reference after deletion.
        List<Long> owners =
                repo.jdbc.query(
                        "SELECT user_id FROM hero_creative WHERE id=? AND deleted_at IS NULL FOR UPDATE",
                        (rs, row) -> rs.getLong(1),
                        id);
        if (owners.isEmpty()) throw HeroException.missing();
        if (owners.getFirst() != user) throw HeroException.forbidden("본인의 소재만 삭제할 수 있습니다.");
        if (repo.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM hero_bid WHERE creative_id=?", Long.class, id)
                > 0) throw HeroException.conflict("입찰에 사용한 소재는 삭제할 수 없습니다.");
        repo.jdbc.update(
                "UPDATE hero_creative SET deleted_at=? WHERE id=?", time(clock.instant()), id);
    }

    Creative creative(long id) {
        return repo
                .jdbc
                .query("SELECT * FROM hero_creative WHERE id=?", HeroRepository::creative, id)
                .stream()
                .findFirst()
                .orElseThrow(HeroException::missing);
    }

    private Slide slide(long id) {
        return repo
                .jdbc
                .query(
                        "SELECT * FROM hero_slide WHERE id=? AND deleted_at IS NULL",
                        HeroRepository::slide,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(HeroException::missing);
    }

    private void lockSettings() {
        repo.jdbc.queryForObject(
                "SELECT id FROM hero_settings WHERE id=1 FOR UPDATE", Integer.class);
    }

    void audit(long actor, String action, Long target, String detail) {
        repo.jdbc.update(
                "INSERT INTO hero_audit(actor_id,action,target_id,detail,created_at) VALUES(?,?,?,?,?)",
                actor,
                action,
                target,
                detail,
                time(clock.instant()));
    }
}
