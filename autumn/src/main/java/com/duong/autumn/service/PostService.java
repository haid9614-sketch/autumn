package com.duong.autumn.service;

import com.duong.autumn.dto.internal.FeedRow;
import com.duong.autumn.dto.request.CreatePostRequest;
import com.duong.autumn.dto.response.*;
import com.duong.autumn.entity.*;
import com.duong.autumn.feed.*;
import com.duong.autumn.repository.*;
import com.duong.autumn.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final FeedCursorCodec cursorCodec;
    private final TrendingFeedSnapshotService snapshotService;
    private final FileUploadService fileUploadService;

    @Transactional
    public void createPost(CreatePostRequest request) {
        Long userId = currentUserId();
        fileUploadService.markImageInUse(request.getImage(), userId);
        User author = userRepository.getReferenceById(userId);
        postRepository.save(Post.builder().image(request.getImage()).name(request.getName())
                .content(request.getContent()).user(author).isDelete(0).build());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FeedResponse getFeed(FeedType requestedType, String token, int limit) {
        return getFeed(requestedType, token, limit, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FeedResponse getFeed(FeedType requestedType, String token, int limit, String feedSessionId) {
        if (requestedType == null || limit < 1 || limit > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Loại feed phải hợp lệ và limit nằm trong khoảng 1 đến 50.");
        }
        Long userId = currentUserId();
        FeedCursor cursor = cursorCodec.decodeForRequest(token, requestedType, userId);
        FeedType type = requestedType;
        if (cursor != null) {
            // Cursor cố định chế độ của phiên; không đổi type theo số follow mới.
            type = cursor.type();
        } else if (requestedType == FeedType.STANDARD
                && followRepository.countByFollowerId(userId) == 0) {
            type = FeedType.TRENDING;
        }

        LocalDateTime afterTime = cursor == null ? null : cursor.afterCreatedAt();
        Long afterId = cursor == null ? null : cursor.afterId();
        PageRequest batchLimit = PageRequest.of(0, limit + 1);
        String snapshotId = null;
        String sessionId = null;
        List<FeedRow> rows;
        switch (type) {
            case STANDARD -> rows = postRepository.getStandardFeed(userId, afterTime, afterId, batchLimit);
            case LIKED -> rows = postRepository.getLikedFeed(userId, afterTime, afterId, batchLimit);
            case TRENDING -> {
                TrendingFeedSessionRef session = snapshotService.open(userId, cursor, feedSessionId);
                snapshotId = session.snapshotId();
                sessionId = session.sessionId();
                Long score = cursor == null ? null : cursor.afterLikeCount();
                rows = postRepository.getTrendingFeed(userId, snapshotId, score, afterTime, afterId, batchLimit);
            }
            default -> throw new IllegalStateException("Unsupported feed type");
        }
        boolean hasMore = rows.size() > limit;
        List<FeedRow> visible = rows.subList(0, Math.min(limit, rows.size()));
        List<PostResponse> items = visible.stream().map(FeedRow::getPost).toList();
        String nextCursor = null;
        if (hasMore) {
            FeedRow last = visible.get(visible.size() - 1);
            nextCursor = cursorCodec.encode(new FeedCursor(2, type, userId,
                    last.getCursorCreatedAt(), last.getCursorId(),
                    last.getCursorLikeCount(), snapshotId, sessionId));
        }
        return new FeedResponse(items, nextCursor, hasMore, sessionId);
    }

    public void closeFeedSession(String feedSessionId) {
        snapshotService.release(currentUserId(), feedSessionId);
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails details)
                || details.getId() == null || !details.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập.");
        }
        return details.getId();
    }
}
