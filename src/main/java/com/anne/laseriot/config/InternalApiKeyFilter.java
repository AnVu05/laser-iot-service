package com.anne.laseriot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final String internalSecretKey;

    public InternalApiKeyFilter(@Value("${app.internal-secret-key:laser-internal-secret-key-change-me}") String internalSecretKey) {
        this.internalSecretKey = internalSecretKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Chỉ kiểm tra bảo mật đối với các endpoint nội bộ /api/v1/internal/**
        if (path.startsWith("/api/v1/internal")) {
            String apiKey = request.getHeader("X-Internal-Secret-Key");

            if (apiKey == null || !apiKey.equals(internalSecretKey)) {
                log.warn("[INTERNAL API] Từ chối truy cập trái phép tới {} từ IP: {}", path, request.getRemoteAddr());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("""
                        {
                            "success": false,
                            "message": "Truy cập bị từ chối: Khóa bí mật nội bộ X-Internal-Secret-Key không hợp lệ",
                            "data": null
                        }
                        """);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
