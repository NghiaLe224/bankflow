package com.bankflow.security.jwt;

import com.bankflow.common.exception.ErrorResponse;
import com.bankflow.security.jwt.exception.InvalidJwtTokenException;
import com.bankflow.security.jwt.exception.JwtTokenExpiredException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            ObjectMapper objectMapper
    ) {
        this.jwtTokenService = jwtTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(7);

        Claims claims;
        try {
            claims = jwtTokenService.parseAccessToken(token);

        } catch (JwtTokenExpiredException e) {
            SecurityContextHolder.clearContext();
            writeUnauthorizedResponse(
                    response,
                    "TOKEN_EXPIRED",
                    e.getMessage()
            );
            return;
        } catch (InvalidJwtTokenException e) {
            SecurityContextHolder.clearContext();
            writeUnauthorizedResponse(
                    response,
                    "INVALID_TOKEN",
                    e.getMessage()
            );
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String subject = claims.getSubject();
            String role =
                    claims.get("role", String.class);
            SimpleGrantedAuthority authority =
                    new SimpleGrantedAuthority(
                            "ROLE_" + role
                    );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            subject,
                            null,
                            List.of(authority)
                    );

            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorizedResponse(
            HttpServletResponse response,
            String code,
            String message
    ) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse =
                new ErrorResponse(
                        code,
                        message,
                        LocalDateTime.now()
                );

        objectMapper.writeValue(
                response.getWriter(),
                errorResponse
        );
    }
}
