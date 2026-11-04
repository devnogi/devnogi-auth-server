package until.the.eternity.das.search.presentation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import until.the.eternity.das.common.response.CommonResponse;
import until.the.eternity.das.search.application.UserSearchService;
import until.the.eternity.das.search.dto.UserSearchResponse;

@RestController
@RequestMapping("/api/user/search")
@RequiredArgsConstructor
public class UserSearchController {

    private final UserSearchService userSearchService;

    @GetMapping
    public ResponseEntity<CommonResponse<List<UserSearchResponse>>> search(
            @RequestParam @NotBlank @Size(max = 50) String query,
            @RequestParam(defaultValue = "10") @Min(1) @Max(10) int limit) {
        return ResponseEntity.ok(CommonResponse.success(userSearchService.search(query, limit)));
    }
}
