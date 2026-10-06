package com.duong.autumn.controller;

import com.duong.autumn.dto.request.CreatePostRequest;
import com.duong.autumn.dto.response.MessageResponse;
import com.duong.autumn.dto.response.PostResponse;
import com.duong.autumn.service.FileUploadService;
import com.duong.autumn.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final FileUploadService fileUploadService;
    private final PostService postService;

    // Upload Ảnh
    @PostMapping("/images")
    public ResponseEntity<Map<String, String>> uploadPostImage(@RequestParam("file") MultipartFile file) {
        String imageUrl = fileUploadService.uploadImage(file, "autumn_posts");
        Map<String, String> response = new HashMap<>();
        response.put("imageUrl", imageUrl);
        return ResponseEntity.ok(response);
    }

    // API 2: Xóa ảnh rác (Frontend sẽ gọi khi người dùng Hủy bài viết)
    @DeleteMapping("/images")
    public ResponseEntity<MessageResponse> deleteOrphanedImage(@RequestParam("imageUrl") String imageUrl) {
        fileUploadService.deleteImage(imageUrl);
        return ResponseEntity.ok(new MessageResponse("Đã dọn dẹp ảnh rác"));
    }

    // Đăng bài
    @PostMapping
    public ResponseEntity<MessageResponse> createPost(@Valid @RequestBody CreatePostRequest request) {
        postService.createPost(request);
        return ResponseEntity.ok(new MessageResponse("Đăng bài viết thành công"));
    }

}
