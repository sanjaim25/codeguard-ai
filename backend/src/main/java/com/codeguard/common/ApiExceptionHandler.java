package com.codeguard.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(com.codeguard.github.WebhookRejectedException.class)
    ProblemDetail webhook(com.codeguard.github.WebhookRejectedException failure) { return problem(failure.status(), failure.getMessage()); }
    @ExceptionHandler(com.codeguard.auth.AuthenticationExpiredException.class)
    ProblemDetail expired() { return problem(HttpStatus.UNAUTHORIZED, "Please sign in with GitHub again"); }
    @ExceptionHandler(com.codeguard.github.GithubApiException.class)
    ProblemDetail github() { return problem(HttpStatus.BAD_GATEWAY, "GitHub could not complete the request. Please try again later."); }
    @ExceptionHandler({ResourceNotFoundException.class, org.springframework.web.servlet.resource.NoResourceFoundException.class,
        org.springframework.web.servlet.NoHandlerFoundException.class})
    ProblemDetail missing() { return problem(HttpStatus.NOT_FOUND, "Resource not found or access has expired"); }
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden() { return problem(HttpStatus.FORBIDDEN, "This action requires repository write access"); }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
        MethodArgumentTypeMismatchException.class, IllegalArgumentException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class})
    ProblemDetail invalid() { return problem(HttpStatus.BAD_REQUEST, "Request validation failed. Check the supplied fields and policy settings."); }
    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class, IllegalStateException.class})
    ProblemDetail conflict() { return problem(HttpStatus.CONFLICT, "The resource changed or already exists. Refresh and try again."); }
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        // Exception messages and stack traces can contain provider payloads, credentials or private code.
        log.error("event=api_failure exception_type={}", exception.getClass().getSimpleName());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again.");
    }
    private ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
