package com.duong.autumn.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {
    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    @Value("${application.security.jwt.refresh-expiration:2592000000}")
    private long refreshExpiration;

    private Key signingKey;

    public record TokenPair(String accessToken, String refreshToken,
                            Instant accessExpiresAt, Instant refreshExpiresAt) {}

    @PostConstruct
    public void initializeSigningKey() {
        try {
            byte[] bytes = Decoders.BASE64.decode(secretKey);
            if (bytes.length < 32 || jwtExpiration <= 0 || refreshExpiration <= jwtExpiration) {
                throw new IllegalArgumentException("JWT configuration does not satisfy requirements");
            }
            signingKey = Keys.hmacShaKeyFor(bytes);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "JWT_SECRET phải là Base64 của ít nhất 32 byte; 0 < hạn access < hạn refresh.", exception);
        }
    }

    // Giữ tên hàm cũ cho những nơi đang cấp/kiểm thử access token.
    public String generateToken(UserDetails userDetails) {
        long now = System.currentTimeMillis();
        return createToken(userDetails, "access", null, now, Math.addExact(now, jwtExpiration));
    }

    public TokenPair issueTokenPair(CustomUserDetails details, String family) {
        long now = System.currentTimeMillis();
        long accessEnd = Math.addExact(now, jwtExpiration);
        long refreshEnd = Math.addExact(now, refreshExpiration);
        return new TokenPair(createToken(details, "access", null, now, accessEnd),
                createToken(details, "refresh", family, now, refreshEnd),
                Instant.ofEpochSecond(accessEnd / 1000), Instant.ofEpochSecond(refreshEnd / 1000));
    }

    private String createToken(UserDetails details, String type, String family, long now, long expiresAt) {
        Map<String, Object> claims = new HashMap<>();
        int version = details instanceof CustomUserDetails custom && custom.getUser().getTokenVersion() != null
                ? custom.getUser().getTokenVersion() : 0;
        claims.put("tokenVersion", version);
        claims.put("tokenType", type);
        if ("refresh".equals(type)) {
            if (!(details instanceof CustomUserDetails custom) || custom.getId() == null || family == null) {
                throw new IllegalArgumentException("Refresh token cần userId và family.");
            }
            claims.put("userId", custom.getId());
            claims.put("refreshFamily", family);
        }
        return Jwts.builder().setClaims(claims)
                .setSubject(details.getUsername()).setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date(now)).setExpiration(new Date(expiresAt))
                .signWith(signingKey, SignatureAlgorithm.HS256).compact();
    }

    private Claims parseSignedClaims(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token).getBody();
        if (claims.getSubject() == null || claims.getSubject().isBlank() || claims.getExpiration() == null) {
            throw new MalformedJwtException("Token thiếu subject hoặc expiration.");
        }
        return claims;
    }

    // Filter API chỉ chấp nhận access. Token cũ chưa có tokenType vẫn tương thích.
    public Claims parseValidatedClaims(String token) {
        Claims claims = parseSignedClaims(token);
        String type = claims.get("tokenType", String.class);
        if (type != null && !"access".equals(type)) {
            throw new MalformedJwtException("Cần access token.");
        }
        tokenVersion(claims, true);
        return claims;
    }

    public Claims parseRefreshClaims(String token) {
        Claims claims = parseSignedClaims(token);
        if (!"refresh".equals(claims.get("tokenType", String.class))
                || claims.getId() == null || claims.getId().isBlank()) {
            throw new MalformedJwtException("Cần refresh token.");
        }
        String family = claims.get("refreshFamily", String.class);
        if (family == null) throw new MalformedJwtException("Refresh token thiếu family.");
        UUID.fromString(family);
        refreshUserId(claims);
        tokenVersion(claims, false);
        return claims;
    }

    public long refreshUserId(Claims claims) {
        Object raw = claims.get("userId");
        if (!(raw instanceof Integer || raw instanceof Long) || ((Number) raw).longValue() <= 0) {
            throw new MalformedJwtException("Refresh token thiếu userId hợp lệ.");
        }
        return ((Number) raw).longValue();
    }

    public int tokenVersion(Claims claims, boolean allowLegacy) {
        Object raw = claims.get("tokenVersion");
        if (raw == null && allowLegacy) return 0;
        if (!(raw instanceof Integer || raw instanceof Long)
                || ((Number) raw).longValue() < 0 || ((Number) raw).longValue() > Integer.MAX_VALUE) {
            throw new MalformedJwtException("Token thiếu tokenVersion hợp lệ.");
        }
        return ((Number) raw).intValue();
    }

    public boolean isTokenVersionValid(Claims claims, UserDetails details) {
        if (!(details instanceof CustomUserDetails custom) || custom.getUser() == null) return false;
        int expected = custom.getUser().getTokenVersion() == null ? 0 : custom.getUser().getTokenVersion();
        return tokenVersion(claims, true) == expected;
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

    public boolean isTokenValid(String token, UserDetails details) {
        try {
            Claims claims = parseValidatedClaims(token);
            return details != null && details.getUsername().equals(claims.getSubject())
                    && isTokenVersionValid(claims, details);
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }
}
