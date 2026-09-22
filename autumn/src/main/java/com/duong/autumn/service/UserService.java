package com.duong.autumn.service;

import com.duong.autumn.dto.response.UserProfileResponse;
import com.duong.autumn.entity.User;
import com.duong.autumn.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    public UserProfileResponse getMyProfile() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        User currentUser = userDetails.getUser();

        return UserProfileResponse.builder()
                .id(currentUser.getId())
                .name(currentUser.getName())
                .username(currentUser.getUsername())
                .email(currentUser.getEmail())
                .avatar(currentUser.getAvatar())
                .sex(currentUser.getSex())
                .dateOfBirth(currentUser.getDateOfBirth())
                .build();
    }
}