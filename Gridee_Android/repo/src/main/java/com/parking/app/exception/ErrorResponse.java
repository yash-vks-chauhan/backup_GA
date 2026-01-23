//package com.parking.app.exception;
//
//import java.time.Instant;
//
///**
// * Standard structure for API error responses.
// */
//public class ErrorResponse {
//
//    // A short title or category of the error
//    private String error;
//
//    // Human-readable message (the detailed reason)
//    private String message;
//
//    // Unique request identifier set by your interceptor
//    private String requestId;
//
//    // Optional: a timestamp for debugging/logging
//    private Instant timestamp;
//
//    public ErrorResponse() {
//        this.timestamp = Instant.now();
//    }
//
//    public ErrorResponse(String error, String message, String requestId) {
//        this.error = error;
//        this.message = message;
//        this.requestId = requestId;
//        this.timestamp = Instant.now();
//    }
//
//    // ----- Getters and Setters -----
//    public String getError() {
//        return error;
//    }
//
//    public void setError(String error) {
//        this.error = error;
//    }
//
//    public String getMessage() {
//        return message;
//    }
//
//    public void setMessage(String message) {
//        this.message = message;
//    }
//
//    public String getRequestId() {
//        return requestId;
//    }
//
//    public void setRequestId(String requestId) {
//        this.requestId = requestId;
//    }
//
//    public Instant getTimestamp() {
//        return timestamp;
//    }
//
//    public void setTimestamp(Instant timestamp) {
//        this.timestamp = timestamp;
//    }
//}
