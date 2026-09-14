package kr.releasepilot.controlplane.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import kr.releasepilot.controlplane.policy.PolicyService;
import kr.releasepilot.controlplane.identity.RateLimitExceededException;
import kr.releasepilot.controlplane.identity.SessionManagementService;
import org.springframework.http.ResponseEntity;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(SessionManagementService.SessionStoreUnavailableException.class)
    ProblemDetail sessionStoreUnavailable(SessionManagementService.SessionStoreUnavailableException exception,
                                          HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        problem.setTitle("Session store unavailable");
        problem.setType(URI.create("https://releasepilot.kr/problems/session-store-unavailable"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "SESSION_STORE_UNAVAILABLE");
        return problem;
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> rateLimited(RateLimitExceededException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        problem.setTitle("Authentication rate limit exceeded");
        problem.setType(URI.create("https://releasepilot.kr/problems/rate-limited"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", exception.code());
        problem.setProperty("retryAfterSeconds", exception.retryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(exception.retryAfterSeconds()))
                .body(problem);
    }

    @ExceptionHandler(PolicyService.InvalidPolicyException.class)
    ProblemDetail invalidPolicy(PolicyService.InvalidPolicyException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        problem.setTitle("Invalid policy definition");
        problem.setType(URI.create("https://releasepilot.kr/problems/invalid-policy"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "INVALID_POLICY_DEFINITION");
        problem.setProperty("violations", exception.getViolations());
        return problem;
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Resource not found");
        problem.setType(URI.create("https://releasepilot.kr/problems/not-found"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", exception.getCode());
        return problem;
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Request conflicts with current state");
        problem.setType(URI.create("https://releasepilot.kr/problems/conflict"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", exception.getCode());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more request fields are invalid"
        );
        problem.setTitle("Invalid request");
        problem.setType(URI.create("https://releasepilot.kr/problems/invalid-request"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "INVALID_REQUEST");
        problem.setProperty("violations", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new Violation(error.getField(), error.getDefaultMessage()))
                .toList());
        return problem;
    }

    record Violation(String field, String message) {
    }
}
