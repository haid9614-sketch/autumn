package com.duong.autumn.service;

import com.duong.autumn.dto.internal.FeedRow;
import com.duong.autumn.dto.request.CreatePostRequest;
import com.duong.autumn.dto.response.*;
import com.duong.autumn.entity.*;
import com.duong.autumn.feed.*;
import com.duong.autumn.repository.*;
import com.duong.autumn.search.SearchQuery;
import com.duong.autumn.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class PostService {

    private static final int PREVIEW_CONTENT_MAX_LENGTH = 120;
    private static final String PREVIEW_SUFFIX = "... xem thêm";
    private static final int MAX_FILTER_REFILL_HOPS = 5;

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final FeedCursorCodec cursorCodec;
    private final TrendingFeedSnapshotService snapshotService;
    private final FileUploadService fileUploadService;
    private final UserBlockRepository userBlockRepository;
    private final ContentReportRepository contentReportRepository;

    // Constructor 6 tham số giữ nguyên tương thích 100% cho mọi Unit Test cũ
    public PostService(
            PostRepository postRepository,
            UserRepository userRepository,
            FollowRepository followRepository,
            FeedCursorCodec cursorCodec,
            TrendingFeedSnapshotService snapshotService,
            FileUploadService fileUploadService
    ) {
        this(postRepository, userRepository, followRepository, cursorCodec,
                snapshotService, fileUploadService, null, null);
    }

    // Constructor chính Spring Boot sử dụng khi chạy ứng dụng
    @Autowired
    public PostService(
            PostRepository postRepository,
            UserRepository userRepository,
            FollowRepository followRepository,
            FeedCursorCodec cursorCodec,
            TrendingFeedSnapshotService snapshotService,
            FileUploadService fileUploadService,
            UserBlockRepository userBlockRepository,
            ContentReportRepository contentReportRepository
    ) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.cursorCodec = cursorCodec;
        this.snapshotService = snapshotService;
        this.fileUploadService = fileUploadService;
        this.userBlockRepository = userBlockRepository;
        this.contentReportRepository = contentReportRepository;
    }

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
        PostResponse post = postRepository.findPostDetailById(postId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy bài viết này."));

        Set<Long> hiddenUsers = getHiddenUserIds(userId);
        Set<Long> reportedPosts = getReportedPostIds(userId);
        if (reportedPosts.contains(post.getId()) || hiddenUsers.contains(post.getAuthorId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Không tìm thấy bài viết này.");
        }
        return post;
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

        Set<Long> hiddenUsers = getHiddenUserIds(viewerId);
        if (hiddenUsers.contains(targetUserId)) {
            return new FeedResponse(List.of(), null, false, null);
        }
        Set<Long> reportedPosts = getReportedPostIds(viewerId);

        LocalDateTime afterTime = cursor == null ? null : cursor.afterCreatedAt();
        Long afterId = cursor == null ? null : cursor.afterId();
        PageRequest batchLimit = PageRequest.of(0, limit + 1);

        // Nếu người dùng không có bài báo cáo nào -> chạy đúng 1 truy vấn gốc
        if (reportedPosts.isEmpty()) {
            List<FeedRow> rows = postRepository.getUserPostsFeed(
                    targetUserId, viewerId, afterTime, afterId, batchLimit);
            return buildProfileFeedResponse(rows, limit, viewerId, targetUserId);
        }

        // Nếu có bài báo cáo -> lấy bù an toàn để không làm đứt chuỗi cursor
        List<FeedRow> collected = new ArrayList<>(limit + 1);
        FeedRow lastRawRow = null;
        boolean dbExhausted = false;

        for (int hop = 0; hop < MAX_FILTER_REFILL_HOPS && collected.size() <= limit; hop++) {
            List<FeedRow> batch = postRepository.getUserPostsFeed(
                    targetUserId, viewerId, afterTime, afterId, batchLimit);
            if (batch.isEmpty()) {
                dbExhausted = true;
                break;
            }
            for (FeedRow row : batch) {
                lastRawRow = row;
                if (!reportedPosts.contains(row.getPost().getId())) {
                    collected.add(row);
                    if (collected.size() > limit) {
                        break;
                    }
                }
            }
            if (batch.size() <= limit) {
                dbExhausted = true;
                break;
            }
            afterTime = lastRawRow.getCursorCreatedAt();
            afterId = lastRawRow.getCursorId();
        }

        boolean hasMore = collected.size() > limit || (!dbExhausted && lastRawRow != null);
        List<FeedRow> visible = collected.subList(0, Math.min(limit, collected.size()));
        List<PostResponse> items = visible.stream()
                .map(row -> toPreviewPost(row.getPost()))
                .toList();

        String nextCursor = null;
        if (hasMore) {
            FeedRow anchor = (collected.size() > limit)
                    ? visible.get(visible.size() - 1)
                    : lastRawRow;
            nextCursor = cursorCodec.encode(new FeedCursor(2, FeedType.STANDARD, viewerId,
                    anchor.getCursorCreatedAt(), anchor.getCursorId(),
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
        Long score = cursor == null ? null : cursor.afterLikeCount();
        PageRequest batchLimit = PageRequest.of(0, limit + 1);
        String snapshotId = null;
        String sessionId = null;

        // Mở/kiểm tra phiên Trending đúng 1 lần duy nhất cho mỗi request
        if (type == FeedType.TRENDING) {
            TrendingFeedSessionRef session = snapshotService.open(userId, cursor, feedSessionId);
            snapshotId = session.snapshotId();
            sessionId = session.sessionId();
        }

        Set<Long> hiddenUsers = getHiddenUserIds(userId);
        Set<Long> reportedPosts = getReportedPostIds(userId);

        // Nhánh tối ưu: Khi không có dữ liệu chặn/báo cáo, chạy đúng 1 lệnh gốc 100%
        if (hiddenUsers.isEmpty() && reportedPosts.isEmpty()) {
            List<FeedRow> rows = queryFeedBatch(type, userId, snapshotId, score, afterTime, afterId, batchLimit);
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

        // Nhánh có lọc chặn/báo cáo: Lấy bù (Refill) để bảo toàn limit và nextCursor
        List<FeedRow> collected = new ArrayList<>(limit + 1);
        FeedRow lastRawRow = null;
        boolean dbExhausted = false;

        for (int hop = 0; hop < MAX_FILTER_REFILL_HOPS && collected.size() <= limit; hop++) {
            List<FeedRow> batch = queryFeedBatch(type, userId, snapshotId, score, afterTime, afterId, batchLimit);
            if (batch.isEmpty()) {
                dbExhausted = true;
                break;
            }
            for (FeedRow row : batch) {
                lastRawRow = row;
                PostResponse post = row.getPost();
                if (!hiddenUsers.contains(post.getAuthorId()) && !reportedPosts.contains(post.getId())) {
                    collected.add(row);
                    if (collected.size() > limit) {
                        break;
                    }
                }
            }
            if (batch.size() <= limit) {
                dbExhausted = true;
                break;
            }
            afterTime = lastRawRow.getCursorCreatedAt();
            afterId = lastRawRow.getCursorId();
            score = lastRawRow.getCursorLikeCount();
        }

        boolean hasMore = collected.size() > limit || (!dbExhausted && lastRawRow != null);
        List<FeedRow> visible = collected.subList(0, Math.min(limit, collected.size()));
        List<PostResponse> items = visible.stream()
                .map(row -> toPreviewPost(row.getPost()))
                .toList();
        String nextCursor = null;
        if (hasMore) {
            FeedRow anchor = (collected.size() > limit)
                    ? visible.get(visible.size() - 1)
                    : lastRawRow;
            nextCursor = cursorCodec.encode(new FeedCursor(2, type, userId,
                    anchor.getCursorCreatedAt(), anchor.getCursorId(),
                    anchor.getCursorLikeCount(), snapshotId, sessionId));
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

    // Tìm bài viết (đã lọc bỏ bài báo cáo và bài của người bị chặn)
    @Transactional(readOnly = true)
    public SearchPageResponse<PostResponse> searchPosts(String keyword, int page, int size) {
        Long viewerId = currentUserId();
        SearchQuery query = SearchQuery.of(keyword, page, size);
        var results = postRepository.searchPostsByName(
                query.containsPattern(), viewerId, query.pageable());

        Set<Long> hiddenUsers = getHiddenUserIds(viewerId);
        Set<Long> reportedPosts = getReportedPostIds(viewerId);
        if (hiddenUsers.isEmpty() && reportedPosts.isEmpty()) {
            return SearchPageResponse.from(results.map(row -> toPreviewPost(row.getPost())));
        }

        List<PostResponse> filtered = results.getContent().stream()
                .map(FeedRow::getPost)
                .filter(post -> !hiddenUsers.contains(post.getAuthorId())
                        && !reportedPosts.contains(post.getId()))
                .map(this::toPreviewPost)
                .toList();

        return SearchPageResponse.from(new SliceImpl<>(filtered, query.pageable(), results.hasNext()));
    }

    private List<FeedRow> queryFeedBatch(
            FeedType type,
            Long userId,
            String snapshotId,
            Long score,
            LocalDateTime afterTime,
            Long afterId,
            PageRequest batchLimit
    ) {
        return switch (type) {
            case STANDARD -> postRepository.getStandardFeed(userId, afterTime, afterId, batchLimit);
            case LIKED -> postRepository.getLikedFeed(userId, afterTime, afterId, batchLimit);
            case TRENDING -> postRepository.getTrendingFeed(userId, snapshotId, score, afterTime, afterId, batchLimit);
        };
    }

    private FeedResponse buildProfileFeedResponse(
            List<FeedRow> rows, int limit, Long viewerId, Long targetUserId
    ) {
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

    private Set<Long> getHiddenUserIds(Long userId) {
        if (userBlockRepository == null) {
            return Collections.emptySet();
        }
        return userBlockRepository.findHiddenUserIds(userId);
    }

    private Set<Long> getReportedPostIds(Long userId) {
        if (contentReportRepository == null) {
            return Collections.emptySet();
        }
        return contentReportRepository.findReportedTargetIds(userId, "POST");
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