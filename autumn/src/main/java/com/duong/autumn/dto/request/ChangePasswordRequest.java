package com.duong.autumn.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

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

}
