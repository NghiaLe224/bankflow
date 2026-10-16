package com.bankflow.security.jwt;

import com.bankflow.config.JwtProperties;
import com.bankflow.security.jwt.exception.InvalidJwtTokenException;
import com.bankflow.security.jwt.exception.JwtTokenExpiredException;
import com.bankflow.user.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtTokenService {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        byte[] keyBytes =
                Decoders.BASE64.decode(jwtProperties.secret());
        this.signingKey =
                Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(Long userId, String email, UserRole role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("token_type", "access")
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(jwtProperties.accessTokenExpiration())))
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("token_type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(jwtProperties.refreshTokenExpiration())))
                .signWith(signingKey)
                .compact();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new JwtTokenExpiredException();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidJwtTokenException();
        }

    }

    public Claims parseAccessToken(String token) {
        Claims claims = parseClaims(token);

        String role = claims.get("role", String.class);

        if (role == null || role.isBlank()) {
            throw new InvalidJwtTokenException();
        }

        try {
            UserRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new InvalidJwtTokenException();
        }

        validateRequiredClaims(claims);

        String tokenType = claims.get("token_type", String.class);
        if (!"access".equals(tokenType)) {
            throw new InvalidJwtTokenException();
        }
        return claims;
    }

    public Claims parseRefreshToken(String token) {
        Claims claims = parseClaims(token);

        validateRequiredClaims(claims);

        String tokenType = claims.get("token_type", String.class);
        if (!"refresh".equals(tokenType)) {
            throw new InvalidJwtTokenException();
        }
        return claims;
    }

    private void validateRequiredClaims(Claims claims) {
        if (claims.getSubject() == null
                || claims.getSubject().isBlank()
                || claims.getIssuedAt() == null
                || claims.getExpiration() == null) {

            throw new InvalidJwtTokenException();
        }
    }
}
