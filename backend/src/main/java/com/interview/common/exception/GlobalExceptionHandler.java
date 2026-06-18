package com.interview.common.exception;

import com.interview.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(BusinessException e) {
        log.error("业务异常: {}", e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getCode() >= 1000 ? 500 : e.getErrorCode().getCode())
                .body(Result.error(e.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Result<?>> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity
                .status(401)
                .body(Result.error(e.getMessage()));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity
                .status(500)
                .body(Result.error("系统异常,请稍后重试"));
    }
}
