package com.dreamhomes.haven.ad.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class AdCampaignEditNotAllowedException extends DomainException {

    public AdCampaignEditNotAllowedException() {
        super(HttpStatus.CONFLICT, "Campaign can only be edited while in DRAFT status");
    }
}
