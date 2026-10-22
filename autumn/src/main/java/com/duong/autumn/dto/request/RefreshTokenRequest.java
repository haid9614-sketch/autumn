package com.duong.autumn.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token không được để trống.")
        @Size(max = 4096, message = "Refresh token không hợp lệ.")
        String refreshToken
) {
}
