package com.doceditor.docmanager.config;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Looks for an {@code Authorization: Bearer <token>} header, validates the JWT
 * against
 * the configured {@link JwtDecoder}, and stores the subject as the
 * authenticated principal.
 *
 * If no Authorization header is present, the request continues unauthenticated.
 * If a bearer token is present but invalid, the request is rejected with 401.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtDecoder jwtDecoder;

    public JwtAuthFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No auth header => proceed (authorization rules will decide later)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);

            String userId = jwt.getSubject();
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userId, null,
                    List.of());

            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (Exception e) {
            // Bearer token provided but invalid/expired/signature mismatch
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
