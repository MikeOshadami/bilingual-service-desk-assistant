package ca.oshadami.servicedesk.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import ca.oshadami.servicedesk.extract.ExtractionService;

/**
 * Maps failures to RFC 9457 problem responses without leaking internals or user input.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ExtractionService.ExtractionException.class)
    ProblemDetail extractionFailed(ExtractionService.ExtractionException e) {
        log.warn("Extraction failed: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "The request could not be processed automatically. It should be routed to a person.");
    }

    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail dependencyDown(ResourceAccessException e) {
        log.error("Dependency unavailable: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "A dependency (model server or case system) is unavailable. Try again shortly.");
    }
}
