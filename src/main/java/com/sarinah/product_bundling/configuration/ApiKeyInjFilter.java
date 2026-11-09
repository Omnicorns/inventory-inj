package com.sarinah.product_bundling.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Autentikasi klien sistem (server-to-server) lewat header X-API-KEY.
 *
 * - Header ada dan benar  -> request diautentikasi sebagai ROLE_INJ untuk request ini saja (tanpa session).
 * - Header ada tetapi salah -> langsung 401.
 * - Header tidak ada -> dilanjutkan; akses ditentukan aturan di {@link SecurityConfig} (login session).
 *
 * Filter ini dipasang di dalam rantai Spring Security (bukan filter servlet global) sehingga
 * berlaku untuk semua endpoint yang dilindungi, termasuk PUT /save/inj-spec dan POST /sync.
 * Perbandingan key memakai waktu konstan.
 */
public class ApiKeyInjFilter extends OncePerRequestFilter {

    static final String HEADER = "X-API-KEY";

    private final byte[] expectedKey;

    public ApiKeyInjFilter(String configuredApiKey) {
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            throw new IllegalStateException("api.key wajib diisi (environment API_KEY)");
        }
        this.expectedKey = configuredApiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expectedKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"invalid_api_key\"}");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("api-client", null, List.of(new SimpleGrantedAuthority("ROLE_INJ"))));
        chain.doFilter(request, response);
    }
}
