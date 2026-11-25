package com.duong.autumn.controller;

import com.duong.autumn.dto.request.RegisterDeviceTokenRequest;
import com.duong.autumn.security.CustomUserDetails;
import com.duong.autumn.service.DeviceTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    // Gọi ngay sau khi Login thành công và app lấy được Expo Push Token
    @PostMapping("/token")
    public ResponseEntity<?> registerToken(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RegisterDeviceTokenRequest request
    ) {
        deviceTokenService.registerToken(userDetails.getId(), request);
        return ResponseEntity.ok(Map.of("message", "Đã đăng ký thiết bị nhận thông báo."));
    }

    // Gọi ngay TRƯỚC khi gọi POST /api/auth/logout để chặn lộ thông báo sau khi đăng xuất
    @DeleteMapping("/token")
    public ResponseEntity<?> unregisterToken(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("token") String token
    ) {
        deviceTokenService.unregisterToken(userDetails.getId(), token);
        return ResponseEntity.ok(Map.of("message", "Đã hủy đăng ký thông báo cho thiết bị."));
    }

    @DeleteMapping("/tokens/me")
    public ResponseEntity<?> unregisterAllMyTokens(@AuthenticationPrincipal CustomUserDetails userDetails) {
        deviceTokenService.unregisterAllMyTokens(userDetails.getId());
        return ResponseEntity.ok(Map.of("message", "Đã hủy toàn bộ thiết bị nhận thông báo."));
    }
}
