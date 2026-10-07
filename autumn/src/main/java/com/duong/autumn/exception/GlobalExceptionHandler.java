package com.duong.autumn.exception;

import com.duong.autumn.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException exception) {
        return response(exception.getStatusCode(),
                exception.getReason() == null ? "Yêu cầu không hợp lệ." : exception.getReason());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getAllErrors().forEach(error -> {
            String key = error instanceof FieldError field ? field.getField() : error.getObjectName();
            errors.putIfAbsent(key, error.getDefaultMessage());
        });
        return response(HttpStatus.BAD_REQUEST, errors);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException exception) {
        return response(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập hoặc đăng nhập lại.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataConflict(
            DataIntegrityViolationException exception) {
        log.warn("Xung đột ràng buộc dữ liệu", exception);
        return response(HttpStatus.CONFLICT, "Dữ liệu bị trùng hoặc xung đột. Hãy kiểm tra và thử lại.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleLargeUpload(
            MaxUploadSizeExceededException exception) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "File ảnh hoặc tổng request vượt giới hạn dung lượng.");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadInput(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "JSON, tham số hoặc file gửi lên không hợp lệ.");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException exception) {
        if (exception instanceof ResponseStatusException statusException) {
            return handleResponseStatusException(statusException);
        }
        if (exception instanceof org.springframework.web.ErrorResponse error) {
            return response(error.getStatusCode(), "Yêu cầu không hợp lệ.");
        }
        log.error("Lỗi runtime chưa được xử lý", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Hệ thống gặp lỗi. Hãy thử lại sau.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception exception) {
        if (exception instanceof org.springframework.web.ErrorResponse error) {
            return response(error.getStatusCode(), "Yêu cầu không hợp lệ.");
        }
        log.error("Lỗi hệ thống chưa được xử lý", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Hệ thống gặp lỗi. Hãy thử lại sau.");
    }

    private ResponseEntity<ErrorResponse> response(
            HttpStatusCode status, Object message) {
        HttpStatus known = HttpStatus.resolve(status.value());
        ErrorResponse body =
                ErrorResponse.builder().timestamp(LocalDateTime.now())
                        .status(status.value()).error(known == null ? "Error" : known.getReasonPhrase())
                        .message(message).build();
        return new ResponseEntity<>(body, status);
    }
}