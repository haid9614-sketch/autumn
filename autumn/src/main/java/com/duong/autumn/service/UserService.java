package com.duong.autumn.service;

import com.duong.autumn.dto.response.UserProfileResponse;
import com.duong.autumn.entity.User;
import com.duong.autumn.repository.UserRepository;
import com.duong.autumn.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserProfileResponse getMyProfile() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String emailUser = authentication.getName();

        if(!userRepository.existsByEmail(emailUser)) {
            throw new RuntimeException("tài khoản không tồn tại");
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        return UserProfileResponse.builder()
                .id(userDetails.getId())
                .name(userDetails.getName())
                .username(userDetails.getNameUser())
                .email(userDetails.getUsername())
                .avatar(userDetails.getAvatar())
                .sex(userDetails.getSex())
                .dateOfBirth(userDetails.getDateOfBirth())
                .build();
    }
}