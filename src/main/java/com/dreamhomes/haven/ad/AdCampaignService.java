package com.dreamhomes.haven.ad;

import com.dreamhomes.haven.ad.dto.AdminPatchAdCampaignRequest;
import com.dreamhomes.haven.ad.dto.AdCampaignResponse;
import com.dreamhomes.haven.ad.dto.CreateAdCampaignRequest;
import com.dreamhomes.haven.ad.dto.PatchMyAdCampaignRequest;
import com.dreamhomes.haven.ad.exception.AdCampaignEditNotAllowedException;
import com.dreamhomes.haven.ad.exception.AdCampaignInvalidStatusTransitionException;
import com.dreamhomes.haven.ad.exception.AdCampaignNotFoundException;
import com.dreamhomes.haven.ad.model.AdCampaign;
import com.dreamhomes.haven.ad.model.AdCampaignStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdCampaignService {

    private final AdCampaignRepository adCampaignRepository;

    @Transactional
    public AdCampaignResponse create(Long sponsorUserId, CreateAdCampaignRequest request) {
        AdCampaign saved = adCampaignRepository.save(AdCampaign.builder()
                .sponsorUserId(sponsorUserId)
                .title(request.title().trim())
                .body(trimToNull(request.body()))
                .budgetCents(request.budgetCents())
                .status(AdCampaignStatus.DRAFT)
                .build());
        log.info("adCampaign id={} sponsorUserId={}", saved.getId(), sponsorUserId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<AdCampaignResponse> listMine(Long sponsorUserId, Pageable pageable) {
        return adCampaignRepository.findBySponsorUserIdOrderByCreatedAtDesc(sponsorUserId, pageable)
                .map(AdCampaignService::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<AdCampaignResponse> adminList(Pageable pageable) {
        return adCampaignRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(AdCampaignService::toResponse);
    }

    @Transactional
    public AdCampaignResponse patchMine(Long sponsorUserId, Long id, PatchMyAdCampaignRequest request) {
        AdCampaign c = adCampaignRepository.findByIdAndSponsorUserId(id, sponsorUserId)
                .orElseThrow(() -> new AdCampaignNotFoundException(id));

        boolean wantsFieldEdit = request.title() != null || request.body() != null || request.budgetCents() != null;
        if (wantsFieldEdit && c.getStatus() != AdCampaignStatus.DRAFT) {
            throw new AdCampaignEditNotAllowedException();
        }
        if (request.title() != null) {
            c.setTitle(request.title().trim());
        }
        if (request.body() != null) {
            c.setBody(trimToNull(request.body()));
        }
        if (request.budgetCents() != null) {
            c.setBudgetCents(request.budgetCents());
        }
        if (request.status() != null) {
            if (request.status() == AdCampaignStatus.PENDING_REVIEW) {
                if (c.getStatus() != AdCampaignStatus.DRAFT) {
                    throw new AdCampaignInvalidStatusTransitionException();
                }
                c.setStatus(AdCampaignStatus.PENDING_REVIEW);
            } else {
                throw new AdCampaignInvalidStatusTransitionException();
            }
        }
        return toResponse(adCampaignRepository.save(c));
    }

    @Transactional
    public AdCampaignResponse adminPatch(Long id, AdminPatchAdCampaignRequest request) {
        AdCampaign c = adCampaignRepository.findById(id)
                .orElseThrow(() -> new AdCampaignNotFoundException(id));
        c.setStatus(request.status());
        return toResponse(adCampaignRepository.save(c));
    }

    private static AdCampaignResponse toResponse(AdCampaign c) {
        return new AdCampaignResponse(
                c.getId(),
                c.getSponsorUserId(),
                c.getTitle(),
                c.getBody(),
                c.getStatus(),
                c.getBudgetCents(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }

    private static String trimToNull(String body) {
        if (body == null) {
            return null;
        }
        String t = body.trim();
        return t.isEmpty() ? null : t;
    }
}
