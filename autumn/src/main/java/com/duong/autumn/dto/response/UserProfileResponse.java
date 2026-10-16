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
    private long followerCount;    // Số người đang theo dõi user này
    private long followingCount;   // Số người mà user này đang theo dõi
    private Boolean isFollowing;   // Mình có đang theo dõi user này không (null khi xem /me)
    private Boolean isFollowedBy;  // User này có đang theo dõi mình không (null khi xem /me)
}
