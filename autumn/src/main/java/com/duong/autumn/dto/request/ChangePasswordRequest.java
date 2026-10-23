package com.duong.autumn.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.nio.charset.StandardCharsets;

@Getter
@Setter
public class ChangePasswordRequest {

    @NotBlank(message = "Mật khẩu cũ không được để trống")
    private String oldPassword;

    @NotBlank(message = "Vui lòng nhập mật khẩu mới của bạn")
    @Size(min = 6, message = "Mật khẩu có ít nhất 6 kí tự")
    private String newPassword;

    @NotBlank(message = "Vui lòng xác nhận lại mật khẩu mới")
    private String confirmPassword;

    @JsonIgnore
    @AssertTrue(message = "Mật khẩu mới không được vượt quá 72 byte")
    public boolean isNewPasswordWithinBcryptLimit() {
        return newPassword == null || newPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

}