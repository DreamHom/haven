package com.dreamhomes.haven.promotion.exception;
import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;


public class InvalidPromotionWindowException extends DomainException {
    public InvalidPromotionWindowException() {
        super(HttpStatus.BAD_REQUEST, "Promotion end time must be after start time");
    }
}
