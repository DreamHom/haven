package com.dreamhomes.haven.ad.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class AdCampaignInvalidStatusTransitionException extends DomainException {

    public AdCampaignInvalidStatusTransitionException() {
        super(HttpStatus.CONFLICT, "That status transition is not allowed for this campaign");
    }
}
