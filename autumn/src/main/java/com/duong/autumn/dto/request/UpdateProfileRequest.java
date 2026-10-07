package com.duong.autumn.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
public class UpdateProfileRequest {
    @NotBlank(message = "Tên không được để trống")
    @Size(max = 255, message = "Tên không vượt quá 255 ký tự")
    private String name;

    @Size(max = 255, message = "Giới tính không vượt quá 255 ký tự")
    private String sex;

    @NotNull(message = "Ngày sinh không được để trống")
    @Past(message = "Ngày sinh phải ở trong quá khứ")
    private LocalDate dateOfBirth;
}
