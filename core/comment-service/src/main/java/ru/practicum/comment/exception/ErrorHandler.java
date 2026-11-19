package ru.practicum.comment.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.practicum.feign.exception.RemoteCallBusinessException;
import ru.practicum.feign.exception.RemoteCallTechnicalException;

import java.time.LocalDateTime;

@RestControllerAdvice
@SuppressWarnings("unused")
public class ErrorHandler {

    @ExceptionHandler
    public ResponseEntity<ApiError> methodArgumentNotValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getFieldError();
        if (fieldError == null) {
            return createErrorResponse("", "incorrect field value", HttpStatus.BAD_REQUEST);
        }

        return createErrorResponse(
                String.format(
                        "value of field '%s'=%s is incorrect, cause: %s",
                        fieldError.getField(),
                        fieldError.getRejectedValue(),
                        fieldError.getDefaultMessage()),
                "incorrect field value",
                HttpStatus.BAD_REQUEST);
    }


    @ExceptionHandler
    public ResponseEntity<ApiError> constraintViolation(ConstraintViolationException e) {
        return createErrorResponse(e.getMessage(), "incorrect field value", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler
    public ResponseEntity<ApiError> methodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        return createErrorResponse(e.getMessage(), "incorrect field value", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler
    public ResponseEntity<ApiError> notFound(NotFoundException e) {
        return createErrorResponse("required object was not found", e.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler
    public ResponseEntity<ApiError> unavailableEventUpdate(UnavailableUpdateException e) {
        return createErrorResponse(e.getMessage(), "update is unavailable", HttpStatus.CONFLICT);
    }

    @ExceptionHandler
    public ResponseEntity<ApiError> unexpected(RuntimeException e) {
        return createErrorResponse(
                e.getMessage(),
                "unexpected error",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler
    public ResponseEntity<ApiError> businessFailure(RemoteCallBusinessException e) {
        return createErrorResponse(e.getMessage(), "business failure", HttpStatus.resolve(e.getStatus()));
    }

    @ExceptionHandler
    public ResponseEntity<ApiError> technicalFailure(RemoteCallTechnicalException e) {
        return createErrorResponse(e.getMessage(), "technical failure", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler
    public ResponseEntity<ApiError> openCircuitBreaker(CallNotPermittedException e) {
        return createErrorResponse(e.getMessage(), "service is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }

    private ResponseEntity<ApiError> createErrorResponse(String message, String reason, HttpStatus status) {
        return ResponseEntity.status(status).body(
                new ApiError(
                        message,
                        reason,
                        status.toString(),
                        LocalDateTime.now()
                )
        );
    }
}
