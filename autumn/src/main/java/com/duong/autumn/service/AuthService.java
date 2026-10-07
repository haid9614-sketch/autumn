package com.duong.autumn.service;

import com.duong.autumn.dto.request.*;
import com.duong.autumn.dto.response.*;
import com.duong.autumn.entity.User;
import com.duong.autumn.repository.UserRepository;
import com.duong.autumn.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
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
                .password(passwordEncoder.encode(request.getPassword())).build();
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
}