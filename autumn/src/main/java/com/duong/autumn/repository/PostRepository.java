package com.duong.autumn.repository;

import com.duong.autumn.dto.internal.FeedRow;
import com.duong.autumn.dto.response.PostResponse;
import com.duong.autumn.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("""
            SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
            FROM Post p
            WHERE p.image = :image AND p.isDelete = 0
            """)
    boolean existsByImage(@Param("image") String image);

    @Query("""
            SELECT p FROM Post p
            JOIN p.user u
            WHERE p.id = :postId AND p.isDelete = 0 AND u.isDelete = 0
            """)
    Optional<Post> findActiveById(@Param("postId") Long postId);

    @Query("""
            SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
            FROM Post p
            JOIN p.user u
            WHERE p.id = :postId AND p.isDelete = 0 AND u.isDelete = 0
            """)
    boolean existsActiveById(@Param("postId") Long postId);

    @Query("""
            SELECT COUNT(p) FROM Post p
            WHERE p.user.id = :userId AND p.isDelete = 0 AND p.user.isDelete = 0
            """)
    long countActiveByUserId(@Param("userId") Long userId);

    // Cùng 11 trường PostResponse như code cũ; ba trường cuối là sort key nội bộ.
    String FEED_SELECT = """
            SELECT new com.duong.autumn.dto.internal.FeedRow(
                p.id, p.image, p.name, p.content, p.createdAt,
                u.id, u.name, u.avatar,
                (SELECT COUNT(l) FROM Like l WHERE l.post = p AND l.user.isDelete = 0),
                (SELECT COUNT(c) FROM Comment c
                 WHERE c.post = p AND c.isDelete = 0 AND c.user.isDelete = 0),
                CASE WHEN (SELECT COUNT(l2) FROM Like l2
                           WHERE l2.post = p AND l2.user.id = :currentUserId) > 0
                     THEN true ELSE false END,
            """;

    // Lọc 2 chiều người dùng bị chặn và bài viết đã bị người xem báo cáo.
    String MODERATION_FILTER = """
              AND NOT EXISTS (
                  SELECT ub.id FROM UserBlock ub
                  WHERE (ub.userId = :currentUserId AND ub.blockedUserId = u.id)
                     OR (ub.userId = u.id AND ub.blockedUserId = :currentUserId)
              )
              AND NOT EXISTS (
                  SELECT cr.id FROM ContentReport cr
                  WHERE cr.userId = :currentUserId
                    AND cr.targetType = 'POST'
                    AND cr.targetId = p.id
              )
            """;

    // Xem chi tiết 1 bài viết (trả về full content)
    @Query("""
            SELECT new com.duong.autumn.dto.response.PostResponse(
                p.id, p.image, p.name, p.content, p.createdAt,
                u.id, u.name, u.avatar,
                (SELECT COUNT(l) FROM Like l WHERE l.post = p AND l.user.isDelete = 0),
                (SELECT COUNT(c) FROM Comment c
                 WHERE c.post = p AND c.isDelete = 0 AND c.user.isDelete = 0),
                CASE WHEN (SELECT COUNT(l2) FROM Like l2
                           WHERE l2.post = p AND l2.user.id = :currentUserId) > 0
                     THEN true ELSE false END
            )
            FROM Post p
            JOIN p.user u
            WHERE p.id = :postId
              AND p.isDelete = 0
              AND u.isDelete = 0
            """ + MODERATION_FILTER)
    Optional<PostResponse> findPostDetailById(
            @Param("postId") Long postId,
            @Param("currentUserId") Long currentUserId
    );

    // Lấy danh sách bài viết trên trang cá nhân (của mình hoặc của người khác) theo Cursor
    @Query(FEED_SELECT + """
                p.createdAt, p.id, 0L)
            FROM Post p
            JOIN p.user u
            WHERE p.isDelete = 0
              AND u.isDelete = 0
              AND u.id = :targetUserId
            """ + MODERATION_FILTER + """
              AND (
                  :afterCreatedAt IS NULL
                  OR p.createdAt < :afterCreatedAt
                  OR (p.createdAt = :afterCreatedAt AND p.id < :afterId)
              )
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<FeedRow> getUserPostsFeed(
            @Param("targetUserId") Long targetUserId,
            @Param("currentUserId") Long currentUserId,
            @Param("afterCreatedAt") LocalDateTime afterCreatedAt,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    @Query(FEED_SELECT + """
                p.createdAt, p.id, 0L)
            FROM Post p
            JOIN p.user u
            WHERE p.isDelete = 0
              AND u.isDelete = 0
              AND (u.id = :currentUserId OR u.id IN (
                  SELECT f.following.id FROM Follow f
                  WHERE f.follower.id = :currentUserId
              ))
            """ + MODERATION_FILTER + """
              AND (
                  :afterCreatedAt IS NULL
                  OR p.createdAt < :afterCreatedAt
                  OR (p.createdAt = :afterCreatedAt AND p.id < :afterId)
              )
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<FeedRow> getStandardFeed(
            @Param("currentUserId") Long currentUserId,
            @Param("afterCreatedAt") LocalDateTime afterCreatedAt,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    @Query(FEED_SELECT + """
                myLike.createdAt, myLike.id, 0L)
            FROM Like myLike
            JOIN myLike.post p
            JOIN p.user u
            WHERE p.isDelete = 0
              AND u.isDelete = 0
              AND myLike.user.id = :currentUserId
            """ + MODERATION_FILTER + """
              AND (
                  :afterCreatedAt IS NULL
                  OR myLike.createdAt < :afterCreatedAt
                  OR (myLike.createdAt = :afterCreatedAt AND myLike.id < :afterId)
              )
            ORDER BY myLike.createdAt DESC, myLike.id DESC
            """)
    List<FeedRow> getLikedFeed(
            @Param("currentUserId") Long currentUserId,
            @Param("afterCreatedAt") LocalDateTime afterCreatedAt,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    @Query(FEED_SELECT + """
                item.postCreatedAt, item.postId, item.likeCount)
            FROM TrendingFeedSnapshotItem item
            JOIN Post p ON p.id = item.postId
            JOIN p.user u
            WHERE item.snapshotId = :snapshotId
              AND p.isDelete = 0
              AND u.isDelete = 0
            """ + MODERATION_FILTER + """
              AND (
                  :afterLikeCount IS NULL
                  OR item.likeCount < :afterLikeCount
                  OR (item.likeCount = :afterLikeCount
                      AND item.postCreatedAt < :afterCreatedAt)
                  OR (item.likeCount = :afterLikeCount
                      AND item.postCreatedAt = :afterCreatedAt
                      AND item.postId < :afterId)
              )
            ORDER BY item.likeCount DESC, item.postCreatedAt DESC, item.postId DESC
            """)
    List<FeedRow> getTrendingFeed(
            @Param("currentUserId") Long currentUserId,
            @Param("snapshotId") String snapshotId,
            @Param("afterLikeCount") Long afterLikeCount,
            @Param("afterCreatedAt") LocalDateTime afterCreatedAt,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    @Modifying
    @Query(value = """
            INSERT INTO feed_trending_items
                (snapshot_id, post_id, like_count, post_created_at)
            SELECT :snapshotId, p.id, COUNT(lu.id), p.created_at
            FROM posts p
            JOIN users u ON u.id = p.user_id
            LEFT JOIN likes l ON l.post_id = p.id
            LEFT JOIN users lu ON lu.id = l.user_id AND lu.is_delete = 0
            WHERE p.is_delete = 0 AND u.is_delete = 0
            GROUP BY p.id, p.created_at
            """, nativeQuery = true)
    int populateTrendingSnapshot(@Param("snapshotId") String snapshotId);

    // HQL collate(): chỉ áp dụng so sánh cho tìm kiếm, không đổi collation của bảng.
    @Query(FEED_SELECT + """
                p.createdAt, p.id, 0L)
            FROM Post p
            JOIN p.user u
            WHERE p.isDelete = 0
              AND u.isDelete = 0
              AND collate(p.name as utf8mb4_0900_ai_ci) LIKE :containsPattern ESCAPE '!'
            """ + MODERATION_FILTER + """
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    Slice<FeedRow> searchPostsByName(
            @Param("containsPattern") String containsPattern,
            @Param("currentUserId") Long currentUserId,
            Pageable pageable
    );


}