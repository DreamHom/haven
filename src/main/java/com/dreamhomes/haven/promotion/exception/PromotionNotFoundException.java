package com.dreamhomes.haven.promotion.exception;
import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;


public class PromotionNotFoundException extends DomainException {
    public PromotionNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Promotion not found");
    }
}
