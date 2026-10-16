package com.duong.autumn.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FollowUserResponse {
    private Long id;
    private String name;
    private String username;
    private String avatar;
    private boolean isFollowing;   // Mình có đang follow người này không
    private boolean isFollowedBy;  // Người này có đang follow mình không (để hiện nút "Theo dõi lại" hoặc nút "Xóa follower")
}