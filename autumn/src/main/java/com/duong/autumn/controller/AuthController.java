package com.duong.autumn.controller;

import com.duong.autumn.dto.request.LoginRequest;
import com.duong.autumn.dto.request.RegisterRequest;
import com.duong.autumn.dto.response.AuthResponse;
import com.duong.autumn.dto.response.MessageResponse;
import com.duong.autumn.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.duong.autumn.dto.request.RefreshTokenRequest;
import org.springframework.http.CacheControl;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

   // login (có cấp refresh token)
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache").body(authService.login(request));
    }


    // REFRESH token
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache").body(authService.refresh(request));
    }


    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout() {
        return ResponseEntity.ok(authService.logout());
    }

}
