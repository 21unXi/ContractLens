package com.contractlens.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class SecurityExceptionHandlers {

    private static final Logger log = LoggerFactory.getLogger(SecurityExceptionHandlers.class);

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            if (response.isCommitted()) {
                log.warn("Auth entry point skipped due to committed response, uri={}, dispatcher={}", safeUri(request), request.getDispatcherType());
                return;
            }

            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", request);
        };
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            logAccessDenied(request, accessDeniedException);

            if (response.isCommitted()) {
                log.warn("Access denied handler skipped due to committed response, uri={}, dispatcher={}", safeUri(request), request.getDispatcherType());
                return;
            }

            writeJson(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", request);
        };
    }

    private void logAccessDenied(HttpServletRequest request, AccessDeniedException ex) {
        String authHeader = request.getHeader("Authorization");
        boolean hasBearer = authHeader != null && authHeader.startsWith("Bearer ");
        DispatcherType dispatcherType = request.getDispatcherType();
        log.warn("Access denied, method={}, uri={}, dispatcher={}, hasBearer={}", request.getMethod(), safeUri(request), dispatcherType, hasBearer, ex);
    }

    private static String safeUri(HttpServletRequest request) {
        try {
            return request.getRequestURI();
        } catch (Exception ex) {
            return "unknown";
        }
    }

    private static void writeJson(HttpServletResponse response, int status, String code, HttpServletRequest request) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        String uri = safeUri(request);
        String body = "{\"code\":\"" + code + "\",\"path\":\"" + escapeJson(uri) + "\"}";
        response.getWriter().write(body);
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

