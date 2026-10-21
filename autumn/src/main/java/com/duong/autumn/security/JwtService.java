package com.duong.autumn.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {
    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    private Key signingKey;

    @PostConstruct
    public void initializeSigningKey() {
        try {
            byte[] bytes = Decoders.BASE64.decode(secretKey);
            if (bytes.length < 32 || jwtExpiration <= 0) {
                throw new IllegalArgumentException("JWT configuration does not satisfy requirements");
            }
            signingKey = Keys.hmacShaKeyFor(bytes);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "JWT_SECRET phải là Base64 của ít nhất 32 byte; thời hạn JWT phải lớn hơn 0.",
                    exception);
        }
    }

    public String generateToken(UserDetails userDetails) {
        long now = System.currentTimeMillis();
        Map<String, Object> claims = new HashMap<>();
        if (userDetails instanceof CustomUserDetails customDetails && customDetails.getUser() != null) {
            Integer version = customDetails.getUser().getTokenVersion();
            claims.put("tokenVersion", version != null ? version : 0);
        } else {
            claims.put("tokenVersion", 0);
        }

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + jwtExpiration))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseValidatedClaims(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(signingKey).build()
                .parseClaimsJws(token).getBody();
        if (claims.getSubject() == null || claims.getSubject().isBlank() || claims.getExpiration() == null) {
            throw new MalformedJwtException("Token thiếu subject hoặc expiration.");
        }
        return claims;
    }

    public boolean isTokenVersionValid(Claims claims, UserDetails userDetails) {
        if (!(userDetails instanceof CustomUserDetails customDetails) || customDetails.getUser() == null) {
            return true;
        }
        int expectedVersion = customDetails.getUser().getTokenVersion() != null
                ? customDetails.getUser().getTokenVersion() : 0;
        Number tokenVersionClaim = claims.get("tokenVersion", Number.class);
        int actualVersion = tokenVersionClaim != null ? tokenVersionClaim.intValue() : 0;
        return actualVersion == expectedVersion;
    }

    public String getEmailFromToken(String token) {
        return parseValidatedClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        try {
            parseValidatedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = parseValidatedClaims(token);
            return userDetails != null
                    && userDetails.getUsername().equals(claims.getSubject())
                    && isTokenVersionValid(claims, userDetails);
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }
}