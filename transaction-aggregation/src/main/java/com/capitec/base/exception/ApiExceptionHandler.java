package com.capitec.base.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
//import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * RFC 9457 problem responses. Messages are fixed text or our own validation messages; framework,
 * database, and provider exception messages are never returned to clients.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(TransactionNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(TransactionNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "not-found", "Not found", "The requested transaction does not exist.");
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ProblemDetail> invalidRequest(InvalidRequestException e) {
        return respond(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", e.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> constraintViolation(ConstraintViolationException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request",
                "One or more parameters are invalid.");
        problem.setProperty("violations", violations(e.getConstraintViolations()));
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ProblemDetail> idempotencyInProgress(IdempotencyConflictException e) {
        return respond(HttpStatus.CONFLICT, "idempotency-in-progress", "Request in progress",
                "A request with this Idempotency-Key is still being processed. Retry after it completes.");
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ResponseEntity<ProblemDetail> idempotencyKeyReused(IdempotencyKeyReusedException e) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "idempotency-key-reused", "Idempotency-Key reused",
                "This Idempotency-Key was already used with a different request. Use a new key.");
    }

    @ExceptionHandler(MissingCustomerIdException.class)
    ResponseEntity<ProblemDetail> unauthenticated(MissingCustomerIdException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"")
                .body(problem(HttpStatus.UNAUTHORIZED, "unauthenticated", "Unauthorized",
                        "The access token does not identify a valid customer."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception e) {
        log.error("Unhandled error while processing request", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal server error",
                "An unexpected error occurred. Quote the traceId when reporting it.");
    }
//
//    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
//    ResponseEntity<ProblemDetail> unauthenticated(AuthenticationCredentialsNotFoundException e) {
//        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"")
//                .body(problem(HttpStatus.UNAUTHORIZED, "unauthenticated", "Unauthorized",
//                        "The access token does not identify a valid customer."));
//    }

    private static ResponseEntity<ProblemDetail> respond(HttpStatus status, String type, String title, String detail) {
        return ResponseEntity.status(status).body(problem(status, type, title, detail));
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:problem-type:transaction-aggregation:" + type));
        problem.setTitle(title);
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }

    private static List<Violation> violations(Set<ConstraintViolation<?>> constraintViolations) {
        return constraintViolations.stream()
                .map(v -> new Violation(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
    }

    private static String lastNode(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        return dot < 0 ? propertyPath : propertyPath.substring(dot + 1);
    }

    private record Violation(String parameter, String message) {
    }
}