package com.duong.autumn.service;

import com.duong.autumn.dto.request.*;
import com.duong.autumn.dto.response.*;
import com.duong.autumn.entity.User;
import com.duong.autumn.repository.TrendingFeedSessionRepository;
import com.duong.autumn.repository.UserRepository;
import com.duong.autumn.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final TrendingFeedSessionRepository trendingFeedSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public MessageResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email này đã được sử dụng.");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username này đã có người sử dụng.");
        }
        User user = User.builder()
                .name(request.getName()).email(request.getEmail())
                .username(request.getUsername()).dateOfBirth(request.getDateOfBirth())
                .avatar("https://i.imgur.com/default-avatar.png")
                .password(passwordEncoder.encode(request.getPassword()))
                .tokenVersion(0)
                .build();
        userRepository.save(user);
        return new MessageResponse("Đăng ký tài khoản thành công");
    }

    public AuthResponse login(LoginRequest request) {
        Authentication result;
        try {
            result = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Email hoặc mật khẩu không chính xác, hoặc tài khoản đã bị khóa.");
        }
        if (!(result.getPrincipal() instanceof CustomUserDetails details) || !details.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không thể đăng nhập tài khoản này.");
        }
        User user = details.getUser();
        return AuthResponse.builder()
                .token(jwtService.generateToken(details)).id(user.getId())
                .email(user.getEmail()).username(user.getUsername()).build();
    }

    // Đăng xuất: Vô hiệu hóa JWT hiện tại và đóng toàn bộ phiên Trending đang mở của user
    @Transactional
    public MessageResponse logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails details)
                || details.getId() == null || !details.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập.");
        }

        Long userId = details.getId();
        userRepository.incrementTokenVersion(userId);
        trendingFeedSessionRepository.releaseAllActiveByUserId(userId, Instant.now());
        SecurityContextHolder.clearContext();

        return new MessageResponse("Đăng xuất thành công");
    }
}