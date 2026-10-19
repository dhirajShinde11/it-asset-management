package com.company.itam.config;

import com.company.itam.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final TokenService tokenService;

    public AuthInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Allow CORS preflight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();

        // 1. Whitelisted Public Endpoints
        if (isPublicPath(path)) {
            return true;
        }

        // 2. Extract Auth Token from Authorization or X-Auth-Token header
        String authHeader = request.getHeader("Authorization");
        String tokenHeader = request.getHeader("X-Auth-Token");

        String token = null;
        if (authHeader != null && !authHeader.isBlank()) {
            token = authHeader;
        } else if (tokenHeader != null && !tokenHeader.isBlank()) {
            token = tokenHeader;
        }

        TokenService.UserSession session = tokenService.validateToken(token);

        if (session == null) {
            sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Valid authentication token required.");
            return false;
        }

        // Attach authenticated session to request
        request.setAttribute("currentUser", session);

        // 3. Role-Based Access Control (RBAC): Enforce Admin privilege for sensitive APIs
        if (isAdminOnlyPath(path, request.getMethod())) {
            if (!session.isAdmin()) {
                sendJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden: Administrative privileges required.");
                return false;
            }
        }

        return true;
    }

    private boolean isPublicPath(String path) {
        return path.equals("/") ||
               path.equals("/index.html") ||
               path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/api/auth/login") ||
               path.equals("/api/system/network-info") ||
               path.startsWith("/api/assets/scan/") ||
               path.startsWith("/api/assets/tag/");
    }

    private boolean isAdminOnlyPath(String path, String method) {
        // System and SMTP configuration requires ADMIN
        if (path.startsWith("/api/smtp/") || path.startsWith("/api/emails") || path.startsWith("/api/bulk/")) {
            return true;
        }
        // Deletions on critical resources require ADMIN
        if ("DELETE".equalsIgnoreCase(method)) {
            if (path.startsWith("/api/users") || path.startsWith("/api/assets")) {
                return true;
            }
        }
        // Creating or updating users requires ADMIN
        if (path.startsWith("/api/users") && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
            return true;
        }
        return false;
    }

    private void sendJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":false,\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
