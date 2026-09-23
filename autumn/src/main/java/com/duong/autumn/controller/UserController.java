package com.duong.autumn.controller;

import com.duong.autumn.dto.request.UpdateProfileRequest;
import com.duong.autumn.dto.response.MessageResponse;
import com.duong.autumn.dto.response.UserProfileResponse;
import com.duong.autumn.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser() {
        return ResponseEntity.ok(userService.getMyProfile());
    }

    // thêm avatar
    @PostMapping("/me/avatar")
    public ResponseEntity<MessageResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userService.updateAvatar(file));
    }


    // update thong tin co ban name, sex, birthday
    @PutMapping("/me")
    public ResponseEntity<MessageResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(request));
    }
}
