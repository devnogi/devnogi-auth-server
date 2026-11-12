package until.the.eternity.das.hero;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import until.the.eternity.das.common.response.CommonResponse;
import until.the.eternity.das.hero.HeroModels.*;

@RestController
@RequiredArgsConstructor
public class HeroController {
    private final HeroContentService content;
    private final HeroAuctionService auctions;
    private final java.time.Clock clock;

    @GetMapping("/api/hero")
    public ResponseEntity<CommonResponse<Display>> display() {
        return fresh(content.display(clock.instant()));
    }

    @GetMapping("/api/hero/auctions/current")
    public ResponseEntity<CommonResponse<Auctions>> current() {
        return fresh(auctions.current());
    }

    @GetMapping("/api/user/hero/creatives")
    public CommonResponse<List<Creative>> creatives(@AuthenticationPrincipal Long id) {
        return CommonResponse.success(content.creatives(id));
    }

    @PostMapping("/api/user/hero/creatives")
    public CommonResponse<Creative> creative(
            @AuthenticationPrincipal Long id,
            @RequestParam MultipartFile image,
            @RequestParam String altText,
            @RequestParam(required = false) String linkUrl) {
        return CommonResponse.success(content.createCreative(id, image, altText, linkUrl));
    }

    @DeleteMapping("/api/user/hero/creatives/{creativeId}")
    public CommonResponse<Void> deleteCreative(
            @AuthenticationPrincipal Long id, @PathVariable long creativeId) {
        content.deleteCreative(id, creativeId);
        return CommonResponse.success(null);
    }

    @PostMapping("/api/user/hero/auctions/{auctionId}/bids")
    public CommonResponse<Bid> bid(
            @AuthenticationPrincipal Long id,
            @PathVariable long auctionId,
            @RequestBody BidRequest request) {
        return CommonResponse.success(auctions.bid(id, auctionId, request));
    }

    @GetMapping("/api/user/hero/auctions")
    public CommonResponse<History> history(@AuthenticationPrincipal Long id) {
        return CommonResponse.success(auctions.history(id));
    }

    @GetMapping("/api/admin/hero/slides")
    public CommonResponse<List<Slide>> slides() {
        return CommonResponse.success(content.slides());
    }

    @PostMapping("/api/admin/hero/slides")
    public CommonResponse<Slide> create(
            @AuthenticationPrincipal Long id,
            @RequestParam MultipartFile image,
            @RequestParam String altText,
            @RequestParam(required = false) String linkUrl,
            @RequestParam(required = false) String startsAt,
            @RequestParam(required = false) String endsAt) {
        return CommonResponse.success(
                content.createSlide(id, image, altText, linkUrl, parse(startsAt), parse(endsAt)));
    }

    @PutMapping("/api/admin/hero/slides/{slideId}")
    public CommonResponse<Slide> update(
            @AuthenticationPrincipal Long id,
            @PathVariable long slideId,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam String altText,
            @RequestParam(required = false) String linkUrl,
            @RequestParam(required = false) String startsAt,
            @RequestParam(required = false) String endsAt) {
        return CommonResponse.success(
                content.updateSlide(
                        id, slideId, image, altText, linkUrl, parse(startsAt), parse(endsAt)));
    }

    @DeleteMapping("/api/admin/hero/slides/{slideId}")
    public CommonResponse<Void> delete(
            @AuthenticationPrincipal Long id, @PathVariable long slideId) {
        content.deleteSlide(id, slideId);
        return CommonResponse.success(null);
    }

    @PutMapping("/api/admin/hero/slides/order")
    public CommonResponse<Void> order(
            @AuthenticationPrincipal Long id, @RequestBody OrderRequest request) {
        content.reorder(id, request.ids());
        return CommonResponse.success(null);
    }

    @GetMapping("/api/admin/hero/preview")
    public CommonResponse<Display> preview(@RequestParam String at) {
        if (at.isBlank()) throw HeroException.invalid("미리보기 시각이 필요합니다.");
        return CommonResponse.success(content.display(parse(at)));
    }

    @GetMapping("/api/admin/hero/auctions")
    public CommonResponse<Auctions> adminAuctions() {
        return CommonResponse.success(auctions.all());
    }

    @GetMapping("/api/admin/hero/creatives")
    public CommonResponse<List<Creative>> adminCreatives() {
        return CommonResponse.success(content.creatives(null));
    }

    @PostMapping("/api/admin/hero/creatives/{creativeId}/block")
    public CommonResponse<Void> block(
            @AuthenticationPrincipal Long id,
            @PathVariable long creativeId,
            @RequestBody ReasonRequest request) {
        auctions.block(id, creativeId, request.reason());
        return CommonResponse.success(null);
    }

    @PostMapping("/api/admin/hero/auctions/{auctionId}/cancel")
    public CommonResponse<Void> cancel(
            @AuthenticationPrincipal Long id,
            @PathVariable long auctionId,
            @RequestBody ReasonRequest request) {
        auctions.cancel(id, auctionId, request.reason());
        return CommonResponse.success(null);
    }

    private static Instant parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            throw HeroException.invalid("날짜 형식이 올바르지 않습니다.");
        }
    }

    private static <T> ResponseEntity<CommonResponse<T>> fresh(T data) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CommonResponse.success(data));
    }
}
