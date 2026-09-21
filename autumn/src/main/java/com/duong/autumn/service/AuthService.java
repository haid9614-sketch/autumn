package com.duong.autumn.service;

import com.duong.autumn.dto.request.LoginRequest;
import com.duong.autumn.dto.request.RegisterRequest;
import com.duong.autumn.dto.response.AuthResponse;
import com.duong.autumn.dto.response.MessageResponse;
import com.duong.autumn.entity.User;
import com.duong.autumn.repository.UserRepository;
import com.duong.autumn.security.CustomUserDetails;
import com.duong.autumn.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public MessageResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email này đã được sử dụng");
        }


        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username này đã có người sử dụng");
        }


        User newUser = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .username(request.getUsername())
                .dateOfBirth(request.getDateOfBirth())
                .avatar("https://i.imgur.com/default-avatar.png")
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        userRepository.save(newUser);

        return new MessageResponse("Đăng ký tài khoản thành công");
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {

            throw new RuntimeException("Email hoặc mật khẩu không chính xác");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User không tồn tại"));

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String token = jwtService.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .build();
    }


}