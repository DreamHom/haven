package com.dreamhomes.haven.ad.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class AdCampaignNotFoundException extends DomainException {

    public AdCampaignNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Ad campaign " + id + " was not found");
    }
}
