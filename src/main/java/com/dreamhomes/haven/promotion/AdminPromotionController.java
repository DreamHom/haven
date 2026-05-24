package com.dreamhomes.haven.promotion;
import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.promotion.dto.ApprovePromotionRequest;
import com.dreamhomes.haven.promotion.dto.PromotionActionRequest;
import com.dreamhomes.haven.promotion.dto.PromotionMetricsResponse;
import com.dreamhomes.haven.promotion.dto.PromotionMetricsSummaryResponse;
import com.dreamhomes.haven.promotion.dto.PromotionResponse;
import com.dreamhomes.haven.promotion.model.PromotionPlacement;
import com.dreamhomes.haven.promotion.model.PromotionStatus;
import com.dreamhomes.haven.promotion.model.PromotionTargetType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/admin/promotions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Admin")
public class AdminPromotionController {

    private final PromotionService promotionService;

    @GetMapping
    public Page<PromotionResponse> search(@RequestParam(required = false) PromotionStatus status,
                                          @RequestParam(required = false) PromotionTargetType targetType,
                                          @RequestParam(required = false) PromotionPlacement placement,
                                          @RequestParam(required = false) Long createdByUserId,
                                          @PageableDefault(size = 20) Pageable pageable) {
        return promotionService.adminSearch(status, targetType, placement, createdByUserId, pageable);
    }

    @GetMapping("/{id}")
    public PromotionResponse find(@AuthenticationPrincipal JwtPrincipal principal,
                                  @PathVariable Long id) {
        return promotionService.findMineOrAdmin(principal.userId(), principal.role(), id);
    }

    @PostMapping("/{id}/approve")
    public PromotionResponse approve(@AuthenticationPrincipal JwtPrincipal principal,
                                     @PathVariable Long id,
                                     @RequestBody(required = false) ApprovePromotionRequest request) {
        Integer priority = request == null ? null : request.priority();
        String reason = request == null ? null : request.reason();
        return promotionService.approve(principal.userId(), id, priority, reason);
    }

    @PostMapping("/{id}/reject")
    public PromotionResponse reject(@AuthenticationPrincipal JwtPrincipal principal,
                                    @PathVariable Long id,
                                    @Valid @RequestBody PromotionActionRequest request) {
        return promotionService.reject(principal.userId(), id, request.reason());
    }

    @PostMapping("/{id}/pause")
    public PromotionResponse pause(@AuthenticationPrincipal JwtPrincipal principal,
                                   @PathVariable Long id,
                                   @Valid @RequestBody PromotionActionRequest request) {
        return promotionService.pause(principal.userId(), id, request.reason());
    }

    @PostMapping("/{id}/resume")
    public PromotionResponse resume(@AuthenticationPrincipal JwtPrincipal principal,
                                    @PathVariable Long id,
                                    @RequestBody(required = false) PromotionActionRequest request) {
        return promotionService.resume(principal.userId(), id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/revoke")
    public PromotionResponse revoke(@AuthenticationPrincipal JwtPrincipal principal,
                                    @PathVariable Long id,
                                    @Valid @RequestBody PromotionActionRequest request) {
        return promotionService.revoke(principal.userId(), id, request.reason());
    }

    @GetMapping("/{id}/metrics")
    public PromotionMetricsResponse metrics(@AuthenticationPrincipal JwtPrincipal principal,
                                            @PathVariable Long id) {
        return promotionService.metricsMineOrAdmin(principal.userId(), principal.role(), id);
    }

    @GetMapping("/metrics/summary")
    public PromotionMetricsSummaryResponse metricsSummary() {
        return promotionService.adminMetricsSummary();
    }
}