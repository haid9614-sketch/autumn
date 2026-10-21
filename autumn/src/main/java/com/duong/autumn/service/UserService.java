package com.duong.autumn.service;

import com.duong.autumn.dto.request.ChangePasswordRequest;
import com.duong.autumn.dto.request.ChangeUsernameRequest;
import com.duong.autumn.dto.request.UpdateProfileRequest;
import com.duong.autumn.dto.response.MessageResponse;
import com.duong.autumn.dto.response.UserProfileResponse;
import com.duong.autumn.entity.User;
import com.duong.autumn.repository.FollowRepository;
import com.duong.autumn.repository.PostRepository;
import com.duong.autumn.repository.UserRepository;
import com.duong.autumn.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.duong.autumn.dto.response.SearchPageResponse;
import com.duong.autumn.dto.response.UserSearchResponse;
import com.duong.autumn.search.SearchQuery;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final PostRepository postRepository;
    private final FileUploadService fileUploadService;
    private final PasswordEncoder passwordEncoder;

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập lại");
        }
        return userDetails.getId();
    }

    // 1. Xem trang cá nhân của chính mình (/me)
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile() {
        Long currentUserId = getCurrentUserId();
        User currentUser = userRepository.findActiveById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu người dùng"));

        long followerCount = followRepository.countByFollowingId(currentUserId);
        long followingCount = followRepository.countByFollowerId(currentUserId);
        long postCount = postRepository.countActiveByUserId(currentUserId);

        return UserProfileResponse.builder()
                .id(currentUser.getId())
                .name(currentUser.getName())
                .username(currentUser.getUsername())
                .email(currentUser.getEmail())
                .avatar(currentUser.getAvatar())
                .sex(currentUser.getSex())
                .dateOfBirth(currentUser.getDateOfBirth())
                .postCount(postCount)
                .followerCount(followerCount)
                .followingCount(followingCount)
                .build();
    }

    // 2. Xem trang cá nhân của người dùng khác (/api/users/{userId})
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long targetUserId) {
        Long currentUserId = getCurrentUserId();
        User targetUser = userRepository.findActiveById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng này"));

        long followerCount = followRepository.countByFollowingId(targetUserId);
        long followingCount = followRepository.countByFollowerId(targetUserId);
        long postCount = postRepository.countActiveByUserId(targetUserId);

        boolean isSelf = currentUserId.equals(targetUserId);
        Boolean isFollowing = isSelf ? null : followRepository.existsByFollowerIdAndFollowingId(currentUserId, targetUserId);
        Boolean isFollowedBy = isSelf ? null : followRepository.existsByFollowerIdAndFollowingId(targetUserId, currentUserId);

        return UserProfileResponse.builder()
                .id(targetUser.getId())
                .name(targetUser.getName())
                .username(targetUser.getUsername())
                .email(isSelf ? targetUser.getEmail() : null) // Bảo mật: Không lộ email của người khác
                .avatar(targetUser.getAvatar())
                .sex(targetUser.getSex())
                .dateOfBirth(isSelf ? targetUser.getDateOfBirth() : null) // Bảo mật: Không lộ ngày sinh người khác
                .postCount(postCount)
                .followerCount(followerCount)
                .followingCount(followingCount)
                .isFollowing(isFollowing)
                .isFollowedBy(isFollowedBy)
                .build();
    }

    // 3. Cập nhật avatar
    public MessageResponse updateAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File ảnh không được để trống");
        }

        Long currentUserId = getCurrentUserId();

        //  Upload lên Cloudinary ngoài Transaction DB để không làm nghẽn kết nối
        String newImageUrl = fileUploadService.uploadImage(file, "autumn_avatars");

        //  Đánh dấu ảnh mới là ATTACHED và cập nhật DB
        User currentUser = userRepository.findActiveById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu người dùng"));

        String oldAvatarUrl = currentUser.getAvatar();
        fileUploadService.markImageInUse(newImageUrl, currentUserId);

        currentUser.setAvatar(newImageUrl);
        userRepository.saveAndFlush(currentUser);

        // Dọn dẹp avatar cũ trên Cloudinary (nếu là ảnh thuộc hệ thống và không còn dùng ở đâu khác)
        fileUploadService.cleanupReplacedAvatar(oldAvatarUrl, currentUserId);

        return new MessageResponse("Cập nhật ảnh đại diện thành công");
    }

    // 4. Cập nhật thông tin cơ bản (name, sex, birthday)
    @Transactional
    public MessageResponse updateProfile(UpdateProfileRequest request) {
        Long currentUserId = getCurrentUserId();
        User currentUser = userRepository.findActiveById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu người dùng"));

        currentUser.setName(request.getName());
        currentUser.setSex(request.getSex());
        currentUser.setDateOfBirth(request.getDateOfBirth());

        userRepository.save(currentUser);
        return new MessageResponse("Cập nhật thông tin cá nhân thành công");
    }

    // 5. Đổi mật khẩu
    @Transactional
    public MessageResponse changePassword(ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu xác nhận không khớp");
        }

        Long currentUserId = getCurrentUserId();
        User currentUser = userRepository.findActiveById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu người dùng"));

        if (!passwordEncoder.matches(request.getOldPassword(), currentUser.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu cũ không chính xác");
        }

        if (passwordEncoder.matches(request.getNewPassword(), currentUser.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới không được giống mật khẩu cũ");
        }

        currentUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(currentUser);

        return new MessageResponse("Đổi mật khẩu thành công");
    }

    // 6. Đổi username
    @Transactional
    public MessageResponse changeUsername(ChangeUsernameRequest request) {
        Long currentUserId = getCurrentUserId();
        User currentUser = userRepository.findActiveById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu người dùng"));

        if (currentUser.getUsername().equals(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username mới không được trùng với username hiện tại");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username này đã được sử dụng");
        }

        currentUser.setUsername(request.getUsername());
        userRepository.save(currentUser);

        return new MessageResponse("Đổi username thành công");
    }

    // tim nguoi dung user
    @Transactional(readOnly = true)
    public SearchPageResponse<UserSearchResponse> searchUsers(String keyword, int page, int size) {
        getCurrentUserId();
        SearchQuery query = SearchQuery.of(keyword, page, size);
        var results = userRepository.searchActiveUsers(query.keyword(),
                query.containsPattern(), query.prefixPattern(), query.pageable());
        return SearchPageResponse.from(results);
    }



}

