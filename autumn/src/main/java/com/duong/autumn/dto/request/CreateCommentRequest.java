package com.duong.autumn.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCommentRequest {

    @NotBlank(message = "Nội dung bình luận không được để trống")
    @Size(max = 2000, message = "Nội dung bình luận không được vượt quá 2000 ký tự")
    private String content;

    // Null nếu là bình luận gốc; truyền ID của bình luận cần trả lời nếu là Reply
    private Long parentId;
}