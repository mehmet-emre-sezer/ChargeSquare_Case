package com.chargesquare.station.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Her istekte 'Authorization: Bearer <token>' başlığını okur, doğrularsa kimliği
 * SecurityContext'e yerleştirir. Token yoksa/geçersizse istek anonim devam eder;
 * yetki kararını (401/403) filter chain verir.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            authenticate(header.substring(BEARER_PREFIX.length()));
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            Claims claims = jwtService.parse(token);
            var authority = new SimpleGrantedAuthority("ROLE_" + jwtService.roleOf(claims));
            var authentication = new UsernamePasswordAuthenticationToken(
                    claims.getSubject(), null, List.of(authority));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception ignored) {
            // Geçersiz token: kimlik atanmaz, istek anonim kalır ve korumalı uçlar 401 döner.
            SecurityContextHolder.clearContext();
        }
    }
}
