package com.duong.autumn.controller;

import com.duong.autumn.dto.request.ChangePasswordRequest;
import com.duong.autumn.dto.request.ChangeUsernameRequest;
import com.duong.autumn.dto.request.UpdateProfileRequest;
import com.duong.autumn.dto.response.FollowUserResponse;
import com.duong.autumn.dto.response.MessageResponse;
import com.duong.autumn.dto.response.UserProfileResponse;
import com.duong.autumn.service.FollowService;
import com.duong.autumn.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final FollowService followService;

    // Xem trang cá nhân của mình
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser() {
        return ResponseEntity.ok(userService.getMyProfile());
    }

    // Xem trang cá nhân của người dùng khác
    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getUserProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserProfile(userId));
    }

    // Cập nhật avatar
    @PostMapping("/me/avatar")
    public ResponseEntity<MessageResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userService.updateAvatar(file));
    }

    // Cập nhật thông tin cơ bản: name, sex, birthday
    @PutMapping("/me")
    public ResponseEntity<MessageResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(request));
    }

    // Đổi mật khẩu
    @PutMapping("/me/password")
    public ResponseEntity<MessageResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(userService.changePassword(request));
    }

    // Đổi username
    @PutMapping("/me/username")
    public ResponseEntity<MessageResponse> changeUsername(
            @Valid @RequestBody ChangeUsernameRequest request) {
        return ResponseEntity.ok(userService.changeUsername(request));
    }

    // ================= CỤM API FOLLOW CHUẨN INSTAGRAM =================

    // 1. Theo dõi người dùng
    @PostMapping("/{targetUserId}/follow")
    public ResponseEntity<MessageResponse> followUser(@PathVariable Long targetUserId) {
        return ResponseEntity.ok(followService.followUser(targetUserId));
    }

    // 2. Bỏ theo dõi (Unfollow người mình đang theo dõi)
    @DeleteMapping("/{targetUserId}/follow")
    public ResponseEntity<MessageResponse> unfollowUser(@PathVariable Long targetUserId) {
        return ResponseEntity.ok(followService.unfollowUser(targetUserId));
    }

    // 3. Loại bỏ người theo dõi mình (Remove Follower chuẩn Instagram)
    @DeleteMapping("/me/followers/{followerUserId}")
    public ResponseEntity<MessageResponse> removeFollower(@PathVariable Long followerUserId) {
        return ResponseEntity.ok(followService.removeFollower(followerUserId));
    }

    // 4. Lấy danh sách người theo dõi (Followers) của một user
    @GetMapping("/{userId}/followers")
    public ResponseEntity<List<FollowUserResponse>> getFollowers(@PathVariable Long userId) {
        return ResponseEntity.ok(followService.getFollowers(userId));
    }

    // 5. Lấy danh sách đang theo dõi (Following) của một user
    @GetMapping("/{userId}/following")
    public ResponseEntity<List<FollowUserResponse>> getFollowing(@PathVariable Long userId) {
        return ResponseEntity.ok(followService.getFollowing(userId));
    }
}
