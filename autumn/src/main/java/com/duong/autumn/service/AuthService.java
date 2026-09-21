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
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public MessageResponse register(RegisterRequest request) {
        Optional<User> exitstingUser = userRepository.findByEmail(request.getEmail());
        if(exitstingUser.isPresent()) {
            throw new RuntimeException("Email này đã được sử dụng");
        }

        User newUser =new User();
        newUser.setName(request.getName());
        newUser.setEmail(request.getEmail());
        newUser.setUsername(request.getUsername());
        newUser.setDateOfBirth(request.getDateOfBirth());
        newUser.setAvatar("https://i.imgur.com/default-avatar.png");
        String hashPass = passwordEncoder.encode(request.getPassword());
        newUser.setPassword(hashPass);
        userRepository.save(newUser);

       return new MessageResponse("Đăng ký tài khoản thành công");
    }


    public AuthResponse login(LoginRequest request) {
       authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                    request.getEmail(),
                    request.getPassword()
            )
       );
       User user = userRepository.findByEmail(request.getEmail())
               .orElseThrow(() -> new RuntimeException("User không tồn tại"));

       CustomUserDetails userDetails = new CustomUserDetails(user);

       String token = jwtService.generateToken(userDetails);

       return  AuthResponse.builder()
               .token(token)
               .id(user.getId())
               .email(user.getEmail())
               .username(user.getUsername())
               .build();
    }


}
