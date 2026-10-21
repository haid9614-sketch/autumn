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
import com.duong.autumn.search.SearchQuery;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {

    private static final int PREVIEW_CONTENT_MAX_LENGTH = 120;
    private static final String PREVIEW_SUFFIX = "... xem thêm";

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

    // Xem chi tiết 1 bài viết (trả về full content không cắt chữ)
    @Transactional(readOnly = true)
    public PostResponse getPostDetail(Long postId) {
        Long userId = currentUserId();
        return postRepository.findPostDetailById(postId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy bài viết này."));
    }

    // Xóa mềm bài viết (chỉ tác giả mới có quyền xóa)
    @Transactional
    public void deletePost(Long postId) {
        Long userId = currentUserId();
        Post post = postRepository.findActiveById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy bài viết này."));

        if (!post.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bạn không có quyền xóa bài viết của người khác.");
        }

        post.setIsDelete(1);
        postRepository.save(post);
        fileUploadService.cleanupReplacedAvatar(post.getImage(), userId);
    }

    // Lấy danh sách bài viết thu gọn trên trang cá nhân (của người khác)
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public FeedResponse getUserPosts(Long targetUserId, String token, int limit) {
        if (limit < 1 || limit > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Limit phải nằm trong khoảng 1 đến 50.");
        }

        Long viewerId = currentUserId();
        userRepository.findActiveById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy người dùng này."));

        FeedCursor cursor = cursorCodec.decodeForProfile(token, viewerId, targetUserId);

        LocalDateTime afterTime = cursor == null ? null : cursor.afterCreatedAt();
        Long afterId = cursor == null ? null : cursor.afterId();
        PageRequest batchLimit = PageRequest.of(0, limit + 1);

        List<FeedRow> rows = postRepository.getUserPostsFeed(
                targetUserId, viewerId, afterTime, afterId, batchLimit);

        boolean hasMore = rows.size() > limit;
        List<FeedRow> visible = rows.subList(0, Math.min(limit, rows.size()));
        List<PostResponse> items = visible.stream()
                .map(row -> toPreviewPost(row.getPost()))
                .toList();

        String nextCursor = null;
        if (hasMore) {
            FeedRow last = visible.get(visible.size() - 1);
            nextCursor = cursorCodec.encode(new FeedCursor(2, FeedType.STANDARD, viewerId,
                    last.getCursorCreatedAt(), last.getCursorId(),
                    0L, null, null, targetUserId));
        }

        return new FeedResponse(items, nextCursor, hasMore, null);
    }

    // Lấy danh sách bài viết thu gọn của chính mình (không gọi sang UserService)
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public FeedResponse getMyPosts(String token, int limit) {
        return getUserPosts(currentUserId(), token, limit);
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
        List<PostResponse> items = visible.stream()
                .map(row -> toPreviewPost(row.getPost()))
                .toList();
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

    // Cắt ngắn mô tả cho thẻ bài viết thu gọn trên Feed và Trang cá nhân
    private PostResponse toPreviewPost(PostResponse original) {
        String content = original.getContent();
        if (content == null || content.length() <= PREVIEW_CONTENT_MAX_LENGTH) {
            return original;
        }

        int cutIndex = content.lastIndexOf(' ', PREVIEW_CONTENT_MAX_LENGTH);
        if (cutIndex < PREVIEW_CONTENT_MAX_LENGTH / 2) {
            cutIndex = PREVIEW_CONTENT_MAX_LENGTH;
        }
        String previewContent = content.substring(0, cutIndex).stripTrailing() + PREVIEW_SUFFIX;

        return new PostResponse(
                original.getId(),
                original.getImage(),
                original.getName(),
                previewContent,
                original.getCreatedAt(),
                original.getAuthorId(),
                original.getAuthorName(),
                original.getAuthorAvatar(),
                original.getLikeCount(),
                original.getCommentCount(),
                original.getIsLiked()
        );
    }

    // tim bai viet
    @Transactional(readOnly = true)
    public SearchPageResponse<PostResponse> searchPosts(String keyword, int page, int size) {
        Long viewerId = currentUserId();
        SearchQuery query = SearchQuery.of(keyword, page, size);
        var results = postRepository.searchPostsByName(
                query.containsPattern(), viewerId, query.pageable());
        return SearchPageResponse.from(results.map(row -> toPreviewPost(row.getPost())));
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
