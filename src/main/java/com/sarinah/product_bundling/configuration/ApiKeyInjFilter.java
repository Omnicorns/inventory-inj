package com.sarinah.product_bundling.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyInjFilter extends OncePerRequestFilter {

    @Value("${api-key}")
    private String configuredApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // hanya cek untuk path inj-spec (GET/PUT/POST apa pun)
        if (!path.startsWith("/sarinah-forwarder/v1/catalogue/inj-spec")) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKeyHeader = request.getHeader("X-API-KEY");

        if (apiKeyHeader == null || !apiKeyHeader.equals(configuredApiKey)) {
            // kalau tidak ada / tidak cocok → 401
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"invalid_api_key\"}");
            return;
        }

        // kalau OK, lanjut ke filter berikutnya / controller
        filterChain.doFilter(request, response);
    }

    }

