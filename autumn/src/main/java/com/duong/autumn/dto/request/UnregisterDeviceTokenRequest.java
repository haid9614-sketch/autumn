package com.duong.autumn.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnregisterDeviceTokenRequest(
        @NotBlank(message = "Push token không được để trống.")
        @Size(max = 512, message = "Push token không hợp lệ.")
        String token
) {}