package com.duong.autumn.service;

import com.duong.autumn.dto.internal.FeedRow;
import com.duong.autumn.dto.request.CreatePostRequest;
import com.duong.autumn.dto.response.FeedResponse;
import com.duong.autumn.dto.response.PostResponse;
import com.duong.autumn.entity.Post;
import com.duong.autumn.entity.User;
import com.duong.autumn.feed.FeedCursor;
import com.duong.autumn.feed.FeedCursorCodec;
import com.duong.autumn.feed.FeedType;
import com.duong.autumn.repository.FollowRepository;
import com.duong.autumn.repository.PostRepository;
import com.duong.autumn.repository.UserRepository;
import com.duong.autumn.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository; // Đã bổ sung khai báo biến này
    private final FeedCursorCodec cursorCodec;
    private final TrendingFeedSnapshotService snapshotService;

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

    // Cần transaction ghi ở lần đầu mở xu hướng để tạo snapshot.
    // Ở lần tiếp theo, transaction giữ khóa đọc snapshot đến hết batch.
    @Transactional
    public FeedResponse getFeed(FeedType type, String token, int limit) {
        if (limit < 1 || limit > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "limit phải nằm trong khoảng 1 đến 50.");
        }
        Long userId = currentUserId();

        if (type == FeedType.STANDARD) {
            long followingCount = followRepository.countByFollowerId(userId);
            if (followingCount == 0) {
                // Nếu chưa theo dõi ai, ép luồng bên dưới chạy theo logic Trending
                type = FeedType.TRENDING;
            }
        }
        // -------------------------------------------------------------

        FeedCursor cursor = cursorCodec.decode(token, type, userId);
        LocalDateTime afterTime = cursor == null ? null : cursor.afterCreatedAt();
        Long afterId = cursor == null ? null : cursor.afterId();

        // Luôn page = 0: Pageable chỉ dùng để LIMIT, không tăng OFFSET.
        // Lấy dư 1 dòng để biết còn bài hay không, không COUNT tổng số bài.
        PageRequest batchLimit = PageRequest.of(0, limit + 1);
        String snapshotId = null;
        List<FeedRow> rows;

        switch (type) {
            case STANDARD -> rows = postRepository.getStandardFeed(
                    userId, afterTime, afterId, batchLimit);
            case LIKED -> rows = postRepository.getLikedFeed(
                    userId, afterTime, afterId, batchLimit);
            case TRENDING -> {
                snapshotId = snapshotService.open(userId,
                        cursor == null ? null : cursor.snapshotId());
                Long afterScore = cursor == null ? null : cursor.afterLikeCount();
                rows = postRepository.getTrendingFeed(
                        userId, snapshotId, afterScore, afterTime, afterId, batchLimit);
            }
            default -> throw new IllegalArgumentException("Unsupported feed type");
        }

        boolean hasMore = rows.size() > limit;
        List<FeedRow> visible = rows.subList(0, Math.min(limit, rows.size()));
        List<PostResponse> items = visible.stream().map(FeedRow::getPost).toList();
        String nextCursor = null;

        if (hasMore) {
            // Cursor lấy từ bài cuối ĐÃ TRẢ, không lấy từ dòng dư.
            FeedRow last = visible.get(visible.size() - 1);
            nextCursor = cursorCodec.encode(new FeedCursor(
                    1, type, userId, last.getCursorCreatedAt(), last.getCursorId(),
                    last.getCursorLikeCount(), snapshotId));
        }
        return new FeedResponse(items, nextCursor, hasMore);
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập.");
        }
        return userDetails.getId();
    }
}