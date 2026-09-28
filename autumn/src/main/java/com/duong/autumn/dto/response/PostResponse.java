package com.duong.autumn.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class PostResponse {
    // Thông tin tác phẩm
    private Long id;
    private String image;
    private String name;
    private String content;
    private LocalDateTime createdAt;

    // Thông tin tác giả
    private Long authorId;
    private String authorName;
    private String authorAvatar;

    // Thống kê tương tác
    private Long likeCount;
    private Long commentCount;

    // Trạng thái của user đang xem
    private Boolean isLiked;
}
