package com.duong.autumn.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;

@Getter
@Builder
public class UserProfileResponse {
    private Long id;
    private String name;
    private String username;
    private String email;
    private String avatar;
    private String sex;
    private LocalDate dateOfBirth;
}
