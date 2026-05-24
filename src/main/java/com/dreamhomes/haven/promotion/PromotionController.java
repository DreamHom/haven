package com.dreamhomes.haven.promotion;
import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.promotion.dto.CreatePromotionRequest;
import com.dreamhomes.haven.promotion.dto.PromotionMetricsResponse;
import com.dreamhomes.haven.promotion.dto.PromotionPublicResponse;
import com.dreamhomes.haven.promotion.dto.PromotionResponse;
import com.dreamhomes.haven.promotion.dto.PromotionTrackRequest;
import com.dreamhomes.haven.promotion.model.PromotionPlacement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
@Tag(name = "Promotions")
public class PromotionController {

    private final PromotionService promotionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromotionResponse request(@AuthenticationPrincipal JwtPrincipal principal,
                                     @Valid @RequestBody CreatePromotionRequest request) {
        return promotionService.request(principal.userId(), request);
    }

    @GetMapping("/mine")
    public Page<PromotionResponse> mine(@AuthenticationPrincipal JwtPrincipal principal,
                                        @PageableDefault(size = 20) Pageable pageable) {
        return promotionService.listMine(principal.userId(), pageable);
    }

    @GetMapping("/{id}")
    public PromotionResponse find(@AuthenticationPrincipal JwtPrincipal principal,
                                  @PathVariable Long id) {
        return promotionService.findMineOrAdmin(principal.userId(), principal.role(), id);
    }

    @GetMapping("/{id}/metrics")
    public PromotionMetricsResponse metrics(@AuthenticationPrincipal JwtPrincipal principal,
                                            @PathVariable Long id) {
        return promotionService.metricsMineOrAdmin(principal.userId(), principal.role(), id);
    }

    @SecurityRequirements
    @GetMapping("/homepage-featured")
    public Page<PromotionPublicResponse> homepageFeatured(@PageableDefault(size = 10) Pageable pageable) {
        return promotionService.publicFor(PromotionPlacement.HOMEPAGE_FEATURED, pageable);
    }

    @SecurityRequirements
    @GetMapping("/listing-search-top")
    public Page<PromotionPublicResponse> listingSearchTop(@PageableDefault(size = 10) Pageable pageable) {
        return promotionService.publicFor(PromotionPlacement.LISTING_SEARCH_TOP, pageable);
    }

    @SecurityRequirements
    @GetMapping("/agent-directory-top")
    public Page<PromotionPublicResponse> agentDirectoryTop(@PageableDefault(size = 10) Pageable pageable) {
        return promotionService.publicFor(PromotionPlacement.AGENT_DIRECTORY_TOP, pageable);
    }

    @SecurityRequirements
    @PostMapping("/{id}/impression")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void impression(@AuthenticationPrincipal JwtPrincipal principal,
                           @PathVariable Long id,
                           @Valid @RequestBody PromotionTrackRequest request) {
        promotionService.recordImpression(id, request.placement(), principal == null ? null : principal.userId());
    }

    @SecurityRequirements
    @PostMapping("/{id}/click")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void click(@AuthenticationPrincipal JwtPrincipal principal,
                      @PathVariable Long id,
                      @Valid @RequestBody PromotionTrackRequest request) {
        promotionService.recordClick(id, request.placement(), principal == null ? null : principal.userId());
    }
}
