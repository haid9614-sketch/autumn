package com.duong.autumn.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {
    private String token;
    @Builder.Default
    private String type = "Bearer";
    private Long id;
    private String username;
    private String email;

    private String refreshToken;
    private Instant accessTokenExpiresAt;
    private Instant refreshTokenExpiresAt;
}
