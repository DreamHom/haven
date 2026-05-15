package com.dreamhomes.haven.offer.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Withdrawing only applies to PENDING offers. Once the owner has acted
 * (ACCEPTED, DECLINED, COUNTERED) or the applicant has already withdrawn,
 * the offer can't be re-withdrawn — that's an out-of-band renegotiation.
 */
public class OfferNotWithdrawableException extends DomainException {

    public OfferNotWithdrawableException(Long offerId) {
        super(HttpStatus.CONFLICT, "Offer " + offerId + " is not PENDING and cannot be withdrawn");
    }
}
