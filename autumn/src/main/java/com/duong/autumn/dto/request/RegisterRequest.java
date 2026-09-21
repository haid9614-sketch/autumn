package com.duong.autumn.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
public class RegisterRequest {
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 ký tự")
    private String password;

    @NotBlank(message = "Tên hiển thị không được để trống")
    private String name;

    @NotBlank(message = "Username không được để trống")
    @Pattern(regexp = "^[a-zA-Z0-9._-]{3,}$", message = "Username không chứa ký tự đặc biệt, tối thiểu 3 ký tự")
    private String username;

    @NotNull(message = "Ngày sinh không được để trống")
    @Past(message = "Ngày sinh phải là một ngày trong quá khứ")
    private LocalDate dateOfBirth;
}