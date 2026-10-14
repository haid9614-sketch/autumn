package com.duong.autumn.repository;

import com.duong.autumn.dto.internal.FeedRow;
import com.duong.autumn.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    boolean existsByImage(String image);

    // Cùng 11 trường PostResponse như code cũ; ba trường cuối là sort key nội bộ.
    String FEED_SELECT = """
            SELECT new com.duong.autumn.dto.internal.FeedRow(
                p.id, p.image, p.name, p.content, p.createdAt,
                u.id, u.name, u.avatar,
                (SELECT COUNT(l) FROM Like l WHERE l.post = p),
                (SELECT COUNT(c) FROM Comment c
                 WHERE c.post = p AND c.isDelete = 0),
                CASE WHEN (SELECT COUNT(l2) FROM Like l2
                           WHERE l2.post = p AND l2.user.id = :currentUserId) > 0
                     THEN true ELSE false END,
            """;

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
            SELECT :snapshotId, p.id, COUNT(l.id), p.created_at
            FROM posts p
            JOIN users u ON u.id = p.user_id
            LEFT JOIN likes l ON l.post_id = p.id
            WHERE p.is_delete = 0 AND u.is_delete = 0
            GROUP BY p.id, p.created_at
            """, nativeQuery = true)
    int populateTrendingSnapshot(@Param("snapshotId") String snapshotId);
}
