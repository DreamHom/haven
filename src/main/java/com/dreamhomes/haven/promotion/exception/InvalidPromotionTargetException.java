package com.dreamhomes.haven.promotion.exception;
import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;


public class InvalidPromotionTargetException extends DomainException {
    public InvalidPromotionTargetException() {
        super(HttpStatus.BAD_REQUEST, "Promotion target is not valid for this placement or caller");
    }
}
