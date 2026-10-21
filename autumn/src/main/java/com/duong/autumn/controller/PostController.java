package com.duong.autumn.controller;

import com.duong.autumn.dto.request.CreateCommentRequest;
import com.duong.autumn.dto.response.CommentResponse;
import com.duong.autumn.service.CommentService;
import com.duong.autumn.dto.request.CreatePostRequest;
import com.duong.autumn.dto.response.FollowUserResponse;
import com.duong.autumn.dto.response.MessageResponse;
import com.duong.autumn.dto.response.PostResponse;
import com.duong.autumn.service.FileUploadService;
import com.duong.autumn.service.LikeService;
import com.duong.autumn.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.duong.autumn.dto.response.SearchPageResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final FileUploadService fileUploadService;
    private final PostService postService;
    private final LikeService likeService;
    private final CommentService commentService;

    // 1. Upload Ảnh bài viết
    @PostMapping("/images")
    public ResponseEntity<Map<String, String>> uploadPostImage(@RequestParam("file") MultipartFile file) {
        String imageUrl = fileUploadService.uploadImage(file, "autumn_posts");
        Map<String, String> response = new HashMap<>();
        response.put("imageUrl", imageUrl);
        return ResponseEntity.ok(response);
    }

    // 2. Xóa ảnh rác (Frontend gọi khi người dùng Hủy tạo bài viết)
    @DeleteMapping("/images")
    public ResponseEntity<MessageResponse> deleteOrphanedImage(@RequestParam("imageUrl") String imageUrl) {
        fileUploadService.deleteImage(imageUrl);
        return ResponseEntity.ok(new MessageResponse("Đã dọn dẹp ảnh rác"));
    }

    // 3. Đăng bài viết
    @PostMapping
    public ResponseEntity<MessageResponse> createPost(@Valid @RequestBody CreatePostRequest request) {
        postService.createPost(request);
        return ResponseEntity.ok(new MessageResponse("Đăng bài viết thành công"));
    }

    // 4. Xem chi tiết 1 bài viết (Khi ấn vào thẻ bài viết trên Feed hoặc Profile)
    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPostDetail(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.getPostDetail(postId));
    }

    // 5. Xóa bài viết của chính mình
    @DeleteMapping("/{postId}")
    public ResponseEntity<MessageResponse> deletePost(@PathVariable Long postId) {
        postService.deletePost(postId);
        return ResponseEntity.ok(new MessageResponse("Đã xóa bài viết thành công"));
    }

    // Tìm bài viết theo tên; dùng page/size riêng, không dùng cursor của feed
    @GetMapping("/search")
    public ResponseEntity<SearchPageResponse<PostResponse>> searchPosts(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(postService.searchPosts(keyword, page, size));
    }


    // CỤM API TIM BÀI VIẾT

    // 6. Thả tim bài viết
    @PostMapping("/{postId}/likes")
    public ResponseEntity<MessageResponse> likePost(@PathVariable Long postId) {
        return ResponseEntity.ok(likeService.likePost(postId));
    }

    // 7. Bỏ thả tim bài viết
    @DeleteMapping("/{postId}/likes")
    public ResponseEntity<MessageResponse> unlikePost(@PathVariable Long postId) {
        return ResponseEntity.ok(likeService.unlikePost(postId));
    }

    // 8. Xem danh sách người đã thả tim bài viết (Khi bấm nút ">" cạnh biểu tượng tim)
    @GetMapping("/{postId}/likes")
    public ResponseEntity<List<FollowUserResponse>> getPostLikers(@PathVariable Long postId) {
        return ResponseEntity.ok(likeService.getPostLikers(postId));
    }

    // CỤM API BÌNH LUẬN BÀI VIẾT

    // 9. Lấy toàn bộ danh sách bình luận của bài viết (hiển thị khi mở chi tiết bài viết)
    @GetMapping("/{postId}/comments")
    public ResponseEntity<List<CommentResponse>> getPostComments(@PathVariable Long postId) {
        return ResponseEntity.ok(commentService.getPostComments(postId));
    }

    // 10. Viết bình luận mới (hoặc trả lời bình luận nếu có truyền parentId)
    @PostMapping("/{postId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.ok(commentService.createComment(postId, request));
    }

    // 11. Xóa bình luận (chủ bình luận hoặc chủ bài viết)
    @DeleteMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<MessageResponse> deleteComment(
            @PathVariable Long postId,
            @PathVariable Long commentId) {
        return ResponseEntity.ok(commentService.deleteComment(postId, commentId));
    }
}