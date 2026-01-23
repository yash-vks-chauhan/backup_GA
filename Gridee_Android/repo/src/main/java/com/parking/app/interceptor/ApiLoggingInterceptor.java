package com.parking.app.interceptor;

import java.util.Collection;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Enumeration;
import java.util.UUID;

@Component
@Slf4j
public class ApiLoggingInterceptor implements HandlerInterceptor {

    private static final String REQUEST_START_TIME = "requestStartTime";
    private static final String REQUEST_ID = "requestId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        request.setAttribute(REQUEST_START_TIME, startTime);
        request.setAttribute(REQUEST_ID, requestId);

        MDC.put(REQUEST_ID, requestId);

        log.info("=== INCOMING REQUEST ===");
        log.info("Request ID: {}", requestId);
        log.info("Method: {}", request.getMethod());
        log.info("URI: {}", request.getRequestURI());
        log.info("Query String: {}", request.getQueryString());
        log.info("Remote Address: {}", request.getRemoteAddr());
        log.info("Content Type: {}", request.getContentType());

        logHeaders(request);

        // *** DO NOT LOG REQUEST BODY HERE ***
        // Body will be logged safely in filter layer

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) throws Exception {
        long startTime = (Long) request.getAttribute(REQUEST_START_TIME);
        String requestId = (String) request.getAttribute(REQUEST_ID);
        long duration = System.currentTimeMillis() - startTime;

        log.info("=== OUTGOING RESPONSE ===");
        log.info("Request ID: {}", requestId);
        log.info("Status Code: {}", response.getStatus());
        log.info("Content Type: {}", response.getContentType());
        log.info("Duration: {} ms", duration);

        logResponseHeaders(response);

        if (ex != null) {
            log.error("Exception occurred for Request ID: {}", requestId, ex);
        }

        MDC.clear();
        log.info("=== REQUEST COMPLETED ===");
    }

    private void logHeaders(HttpServletRequest request) {
        log.info("Request Headers:");
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            if (isSensitiveHeader(headerName)) {
                headerValue = "***MASKED***";
            }
            log.info("  {}: {}", headerName, headerValue);
        }
    }

    private void logResponseHeaders(HttpServletResponse response) {
        log.info("Response Headers:");
        Collection<String> headerNames = response.getHeaderNames();
        for (String headerName : headerNames) {
            log.info("  {}: {}", headerName, response.getHeader(headerName));
        }
    }

    private boolean isSensitiveHeader(String headerName) {
        return headerName.toLowerCase().contains("authorization") ||
                headerName.toLowerCase().contains("password") ||
                headerName.toLowerCase().contains("token");
    }
}
