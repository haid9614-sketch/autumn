package com.duong.autumn.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class CommentResponse {
    private Long id;
    private Long postId;
    private Long parentId;
    private String content;
    private LocalDateTime createdAt;

    // Thông tin người bình luận
    private Long authorId;
    private String authorName;
    private String authorUsername;
    private String authorAvatar;

    // Cờ báo cho iOS biết user hiện tại có quyền xóa bình luận này không (chủ comment hoặc chủ bài viết)
    private boolean canDelete;

    // Danh sách các bình luận phản hồi (chỉ có ở bình luận gốc cấp 1)
    private List<CommentResponse> replies;
}