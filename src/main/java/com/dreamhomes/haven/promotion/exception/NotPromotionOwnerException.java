package com.dreamhomes.haven.promotion.exception;
import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;


public class NotPromotionOwnerException extends DomainException {
    public NotPromotionOwnerException() {
        super(HttpStatus.FORBIDDEN, "You are not allowed to access this promotion");
    }
}
