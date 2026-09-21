package com.duong.autumn.dto.response;

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
}
