package until.the.eternity.das.experience;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import until.the.eternity.das.common.response.CommonResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user/experience")
public class ExperienceController {
    private final ExperienceQueryService queries;

    @GetMapping
    public CommonResponse<ExperienceQueryService.Summary> summary(
            @AuthenticationPrincipal Long id) {
        return CommonResponse.success(queries.summary(id));
    }

    @GetMapping("/history")
    public CommonResponse<ExperienceQueryService.History> history(
            @AuthenticationPrincipal Long id,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return CommonResponse.success(queries.history(id, cursor, size));
    }
}
