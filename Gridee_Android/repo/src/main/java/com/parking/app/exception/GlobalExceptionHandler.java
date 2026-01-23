//package com.parking.app.exception;
//
//import jakarta.servlet.http.HttpServletRequest;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.MethodArgumentNotValidException;
//import org.springframework.web.bind.annotation.ControllerAdvice;
//import org.springframework.web.bind.annotation.ExceptionHandler;
//
//@ControllerAdvice
//@Slf4j
//public class GlobalExceptionHandler {
//
//    @ExceptionHandler(Exception.class)
//    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
//        String requestId = (String) request.getAttribute("requestId");
//
//        log.error("Unhandled exception for Request ID: {}, URI: {}",
//                requestId, request.getRequestURI(), ex);
//
//        ErrorResponse error = new ErrorResponse(
//                "Internal Server Error",
//                ex.getMessage(),
//                requestId
//        );
//
//        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
//    }
//
//    @ExceptionHandler(MethodArgumentNotValidException.class)
//    public ResponseEntity<ErrorResponse> handleValidationException(
//            MethodArgumentNotValidException ex, HttpServletRequest request) {
//
//        String requestId = (String) request.getAttribute("requestId");
//        log.warn("Validation error for Request ID: {}", requestId, ex);
//
//        ErrorResponse error = new ErrorResponse(
//                "Validation Error",
//                ex.getBindingResult().getFieldError().getDefaultMessage(),
//                requestId
//        );
//
//        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
//    }
//}
//
