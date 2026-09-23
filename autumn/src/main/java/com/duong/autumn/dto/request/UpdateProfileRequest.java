package com.duong.autumn.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateProfileRequest {

    @NotBlank(message = "Tên không được để trống")
    private String name;

    private String sex;

    @Past(message = "Ngày sinh phải ở trong quá khứ")
    private LocalDate dateOfBirth;
}