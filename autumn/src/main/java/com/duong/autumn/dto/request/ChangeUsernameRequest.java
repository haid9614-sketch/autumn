package com.duong.autumn.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ChangeUsernameRequest {

    @NotBlank(message = "Username không được để trống")
    @Pattern(regexp = "^[a-zA-Z0-9._-]{3,}$", message = "Username không chứa ký tự đặc biệt, tối thiểu 3 ký tự")
    @Size(max = 255, message = "Username không được vượt quá 255 ký tự")
    private String username;
}
