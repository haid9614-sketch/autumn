package com.duong.autumn.service;

import com.duong.autumn.dto.response.FollowUserResponse;
import com.duong.autumn.dto.response.MessageResponse;
import com.duong.autumn.entity.Follow;
import com.duong.autumn.entity.User;
import com.duong.autumn.repository.FollowRepository;
import com.duong.autumn.repository.UserRepository;
import com.duong.autumn.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập để thực hiện thao tác này");
        }
        return userDetails.getId();
    }

    // 1. Theo dõi người dùng khác (Idempotent - bấm đúp không lỗi)
    @Transactional
    public MessageResponse followUser(Long targetUserId) {
        Long currentUserId = getCurrentUserId();

        if (currentUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bạn không thể tự theo dõi chính mình");
        }

        User currentUser = userRepository.findActiveById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không hợp lệ"));

        User targetUser = userRepository.findActiveById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng này"));

        if (followRepository.existsByFollowerIdAndFollowingId(currentUserId, targetUserId)) {
            return new MessageResponse("Đã theo dõi người dùng này");
        }

        try {
            Follow follow = Follow.builder()
                    .follower(currentUser)
                    .following(targetUser)
                    .build();
            followRepository.saveAndFlush(follow);
        } catch (DataIntegrityViolationException ignored) {
            // Bắt trường hợp người dùng bấm đúp 2 lần liên tiếp (Race Condition)
        }

        return new MessageResponse("Theo dõi người dùng thành công");
    }

    // 2. Bỏ theo dõi (Unfollow: Mình bỏ theo dõi người B)
    @Transactional
    public MessageResponse unfollowUser(Long targetUserId) {
        Long currentUserId = getCurrentUserId();

        if (currentUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thao tác không hợp lệ");
        }

        followRepository.deleteByFollowerIdAndFollowingId(currentUserId, targetUserId);
        return new MessageResponse("Đã bỏ theo dõi người dùng");
    }

    // 3. Loại bỏ người theo dõi (Remove Follower chuẩn Instagram: Người A đang follow mình, mình xóa A khỏi danh sách follower)
    @Transactional
    public MessageResponse removeFollower(Long followerUserId) {
        Long currentUserId = getCurrentUserId();

        if (currentUserId.equals(followerUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thao tác không hợp lệ");
        }

        // Lưu ý vị trí tham số: follower là người A (followerUserId), following là chính mình (currentUserId)
        followRepository.deleteByFollowerIdAndFollowingId(followerUserId, currentUserId);
        return new MessageResponse("Đã xóa người theo dõi này");
    }

    // 4. Xem danh sách người theo dõi (Followers) của một user bất kỳ
    @Transactional(readOnly = true)
    public List<FollowUserResponse> getFollowers(Long userId) {
        Long currentUserId = getCurrentUserId();

        userRepository.findActiveById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng này"));

        List<User> followers = followRepository.findActiveFollowersByUserId(userId);
        return mapToFollowUserResponses(followers, currentUserId);
    }

    // 5. Xem danh sách đang theo dõi (Following) của một user bất kỳ
    @Transactional(readOnly = true)
    public List<FollowUserResponse> getFollowing(Long userId) {
        Long currentUserId = getCurrentUserId();

        userRepository.findActiveById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng này"));

        List<User> following = followRepository.findActiveFollowingByUserId(userId);
        return mapToFollowUserResponses(following, currentUserId);
    }

    // Hàm phụ trợ chuyển đổi danh sách User sang DTO bằng Batch Query (Chống lỗi N+1)
    private List<FollowUserResponse> mapToFollowUserResponses(List<User> users, Long currentUserId) {
        if (users.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> targetIds = users.stream().map(User::getId).toList();

        Set<Long> myFollowingIds = followRepository.findFollowingIdsIn(currentUserId, targetIds);
        Set<Long> myFollowerIds = followRepository.findFollowerIdsIn(currentUserId, targetIds);

        return users.stream()
                .map(u -> FollowUserResponse.builder()
                        .id(u.getId())
                        .name(u.getName())
                        .username(u.getUsername())
                        .avatar(u.getAvatar())
                        .isFollowing(myFollowingIds.contains(u.getId()))
                        .isFollowedBy(myFollowerIds.contains(u.getId()))
                        .build())
                .toList();
    }
}