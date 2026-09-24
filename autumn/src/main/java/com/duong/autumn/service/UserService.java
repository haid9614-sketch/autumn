package com.duong.autumn.service;

import com.duong.autumn.dto.request.ChangePasswordRequest;
import com.duong.autumn.dto.request.ChangeUsernameRequest;
import com.duong.autumn.dto.response.MessageResponse;
import com.duong.autumn.dto.response.UserProfileResponse;
import com.duong.autumn.entity.User;
import com.duong.autumn.repository.UserRepository;
import com.duong.autumn.security.CustomUserDetails;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.duong.autumn.dto.request.UpdateProfileRequest;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FileUploadService fileUploadService;
    private final PasswordEncoder passwordEncoder;


    // xem trang cá nhân
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


    // update avatar
    public MessageResponse updateAvatar(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("File ảnh không được để trống");
        }

        String imageUrl = fileUploadService.uploadImage(file, "autumn_avatars");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User currentUser = userDetails.getUser();

        currentUser.setAvatar(imageUrl);
        userRepository.save(currentUser);

        return new MessageResponse("Cập nhật ảnh đại diện thành công");
    }


    // update thông tin cơ bản, name, sex, brithday
    @Transactional
    public MessageResponse updateProfile(UpdateProfileRequest request) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long currentUserId = userDetails.getId();


        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy dữ liệu người dùng"));

        currentUser.setName(request.getName());
        currentUser.setSex(request.getSex());
        currentUser.setDateOfBirth(request.getDateOfBirth());

        userRepository.save(currentUser);

        return new MessageResponse("Cập nhật thông tin cá nhân thành công");
    }

    // đổi mật khẩu
    @Transactional
    public MessageResponse changePassword(ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Mật khẩu xác nhận không khớp");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        User currentUser = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy dữ liệu người dùng"));


        if (!passwordEncoder.matches(request.getOldPassword(), currentUser.getPassword())) {
            throw new RuntimeException("Mật khẩu cũ không chính xác");
        }


        if (passwordEncoder.matches(request.getNewPassword(), currentUser.getPassword())) {
            throw new RuntimeException("Mật khẩu mới không được giống mật khẩu cũ");
        }

        currentUser.setPassword(passwordEncoder.encode(request.getNewPassword()));

        userRepository.save(currentUser);

        return new MessageResponse("Đổi mật khẩu thành công");
    }


    // đổi username
    @Transactional
    public MessageResponse changeUsername(ChangeUsernameRequest request) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();

        User currentUser = userRepository.findById(customUserDetails.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy dữ liệu người dùng"));


        if (currentUser.getUsername().equals(request.getUsername())) {
            throw new RuntimeException("Username mới không được trùng với username hiện tại");
        }


        if(userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username này đã được sử dụng"); // Đã sửa lỗi typo "ên"
        }

        currentUser.setUsername(request.getUsername());

        userRepository.save(currentUser);

        return new MessageResponse("Đổi username thành công");
    }

   //

}