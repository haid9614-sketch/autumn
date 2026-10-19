package com.duong.autumn.service;

import com.duong.autumn.dto.response.FollowUserResponse;
import com.duong.autumn.dto.response.MessageResponse;
import com.duong.autumn.entity.Like;
import com.duong.autumn.entity.Post;
import com.duong.autumn.entity.User;
import com.duong.autumn.repository.LikeRepository;
import com.duong.autumn.repository.PostRepository;
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

import java.util.List;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final FollowService followService;

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập");
        }
        return userDetails.getId();
    }

    // 1. Thả tim bài viết (Idempotent - bấm đúp không văng lỗi)
    @Transactional
    public MessageResponse likePost(Long postId) {
        Long currentUserId = getCurrentUserId();

        User currentUser = userRepository.findActiveById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không hợp lệ"));

        Post post = postRepository.findActiveById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết này"));

        if (likeRepository.existsByUserIdAndPostId(currentUserId, postId)) {
            return new MessageResponse("Đã thích bài viết");
        }

        try {
            Like like = Like.builder()
                    .user(currentUser)
                    .post(post)
                    .build();
            likeRepository.saveAndFlush(like);
        } catch (DataIntegrityViolationException ignored) {
            // Chặn lỗi Race Condition khi người dùng bấm đúp tim liên tục
        }

        return new MessageResponse("Thích bài viết thành công");
    }

    // 2. Bỏ thả tim bài viết
    @Transactional
    public MessageResponse unlikePost(Long postId) {
        Long currentUserId = getCurrentUserId();

        if (!postRepository.existsActiveById(postId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết này");
        }

        likeRepository.deleteByUserIdAndPostId(currentUserId, postId);
        return new MessageResponse("Đã bỏ thích bài viết");
    }

    // 3. Xem danh sách người đã thả tim bài viết (Khi bấm nút ">" cạnh biểu tượng tim)
    @Transactional(readOnly = true)
    public List<FollowUserResponse> getPostLikers(Long postId) {
        Long currentUserId = getCurrentUserId();

        if (!postRepository.existsActiveById(postId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết này");
        }

        List<User> likers = likeRepository.findActiveLikersByPostId(postId);
        return followService.mapToFollowUserResponses(likers, currentUserId);
    }
}
