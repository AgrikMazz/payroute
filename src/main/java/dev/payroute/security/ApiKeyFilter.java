package dev.payroute.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {
    private final byte[] configuredKey;
    public ApiKeyFilter(@Value("${payroute.security.api-key}") String key) { this.configuredKey = key.getBytes(StandardCharsets.UTF_8); }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/") || path.startsWith("/api/v1/webhooks/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] supplied = request.getHeader("X-API-Key") == null ? new byte[0]
                : request.getHeader("X-API-Key").getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(configuredKey, supplied)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"A valid X-API-Key is required.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
