package com.dreamhomes.haven.common.web;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Maps {@link DomainException} subclasses to RFC 7807 problem responses with the status
 * each exception declares. Validation (400) and authentication (401) are handled by
 * Spring's defaults; this advice only owns domain-specific failures.
 *
 * <p>Each response carries a stable {@code type} URI so clients can branch on the kind
 * of failure programmatically (e.g. retry on 409 conflict, redirect on 401). The default
 * {@code about:blank} from {@link ProblemDetail#forStatusAndDetail} is replaced with one
 * URI per status family — see {@link #typeFor(HttpStatusCode)}. The namespace base is
 * {@code haven.errors.type-base} (override per environment).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final String errorTypeBase;

    public GlobalExceptionHandler(
            @Value("${haven.errors.type-base:https://github.com/DreamHom/haven/blob/main/docs/errors/}") String errorTypeBase) {
        this.errorTypeBase = errorTypeBase;
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        problem.setType(typeFor(ex.status()));
        return problem;
    }

    /**
     * Optimistic lock conflicts on Listing/Offer (or any future {@code @Version}-locked
     * entity) surface as 409 — same shape as our other duplicate/conflict paths so
     * clients have one retry/recovery story.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "This resource was modified by someone else — reload and retry");
        problem.setType(typeFor(HttpStatus.CONFLICT));
        return problem;
    }

    /** Map an HTTP status to the stable type identifier we publish for that family. */
    private URI typeFor(HttpStatusCode status) {
        return URI.create(errorTypeBase + switch (status.value()) {
            case 400 -> "validation-failed";
            case 401 -> "unauthenticated";
            case 403 -> "forbidden";
            case 404 -> "not-found";
            case 409 -> "conflict";
            case 429 -> "rate-limited";
            default -> "domain-error";
        });
    }
}
