package com.duong.autumn.service;

import com.duong.autumn.dto.request.CreatePostRequest;
import com.duong.autumn.dto.response.PostResponse;
import com.duong.autumn.entity.Post;
import com.duong.autumn.entity.User;
import com.duong.autumn.repository.FollowRepository;
import com.duong.autumn.repository.PostRepository;
import com.duong.autumn.repository.UserRepository;
import com.duong.autumn.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    @Transactional
    public void createPost(CreatePostRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getId();

        User proxyUser = userRepository.getReferenceById(userId);

        Post newPost = Post.builder()
                .image(request.getImage())
                .name(request.getName())
                .content(request.getContent())
                .user(proxyUser)
                .isDelete(0)
                .build();

        postRepository.save(newPost);
    }

    // bang feed
    @Transactional(readOnly = true) // hàm chỉ đọc (SELECT)
    public Page<PostResponse> getFeeds(String feedType, int page, int size) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long currentUserId = userDetails.getId();

        Pageable pageable = PageRequest.of(page, size);

        switch (feedType.toLowerCase()) {
            case "trending":
                return postRepository.getTrendingFeed(currentUserId, pageable);

            case "liked":
                return postRepository.getLikedFeed(currentUserId, pageable);

            case "standard":
            default:

                long followingCount = followRepository.countByFollowerId(currentUserId);

                if (followingCount == 0) {

                    return postRepository.getTrendingFeed(currentUserId, pageable);
                }

                return postRepository.getStandardFeed(currentUserId, pageable);
        }
    }



}
