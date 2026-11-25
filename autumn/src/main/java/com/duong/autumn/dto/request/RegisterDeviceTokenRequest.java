package com.duong.autumn.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterDeviceTokenRequest {

    @NotBlank(message = "Push token không được để trống.")
    @Size(max = 512, message = "Push token không hợp lệ.")
    private String token;

    @NotBlank(message = "Tên thiết bị không được để trống.")
    @Size(max = 100, message = "Tên thiết bị tối đa 100 ký tự.")
    private String model;
}
