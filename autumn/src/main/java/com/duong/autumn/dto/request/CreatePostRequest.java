package com.duong.autumn.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePostRequest {

    @NotBlank(message = "Bài viết bắt buộc phải có ảnh")
    private String image;

    @NotBlank(message = "Tiêu đề bài viết không được để trống")
    @Size(max = 100, message = "Tiêu đề không vượt quá 100 ký tự")
    private String name;

    @NotBlank(message = "Nội dung bài viết không được để trống")
    private String content;
}