package com.dreamhomes.haven.promotion.exception;
import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;


public class InvalidPromotionTransitionException extends DomainException {
    public InvalidPromotionTransitionException() {
        super(HttpStatus.CONFLICT, "Promotion cannot transition to the requested status");
    }
}
