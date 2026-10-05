package com.duong.autumn.repository;

import com.duong.autumn.dto.response.PostResponse;
import com.duong.autumn.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {


    // STANDARD FEED: Người mình Follow + Chính mình
    @Query("""
    SELECT new com.duong.autumn.dto.response.PostResponse(
        p.id,
        p.image,
        p.name,
        p.content,
        p.createdAt,
        u.id,
        u.name,
        u.avatar,

        (SELECT COUNT(l)
         FROM Like l
         WHERE l.post = p),

        (SELECT COUNT(c)
         FROM Comment c
         WHERE c.post = p
           AND c.isDelete = 0),

        CASE
            WHEN (
                SELECT COUNT(l2)
                FROM Like l2
                WHERE l2.post = p
                  AND l2.user.id = :currentUserId
            ) > 0
            THEN true
            ELSE false
        END
    )
    FROM Post p
    JOIN p.user u
    WHERE p.isDelete = 0
      AND (
          u.id = :currentUserId
          OR u.id IN (
              SELECT f.following.id
              FROM Follow f
              WHERE f.follower.id = :currentUserId
          )
      )
    ORDER BY p.createdAt DESC
    """)
    Page<PostResponse> getStandardFeed(
            @Param("currentUserId") Long currentUserId,
            Pageable pageable
    );


    // TRENDING FEED: Toàn hệ thống, sắp xếp theo Like
    @Query("""
    SELECT new com.duong.autumn.dto.response.PostResponse(
        p.id,
        p.image,
        p.name,
        p.content,
        p.createdAt,
        u.id,
        u.name,
        u.avatar,

        (SELECT COUNT(l)
         FROM Like l
         WHERE l.post = p),

        (SELECT COUNT(c)
         FROM Comment c
         WHERE c.post = p
           AND c.isDelete = 0),

        CASE
            WHEN (
                SELECT COUNT(l2)
                FROM Like l2
                WHERE l2.post = p
                  AND l2.user.id = :currentUserId
            ) > 0
            THEN true
            ELSE false
        END
    )
    FROM Post p
    JOIN p.user u
    WHERE p.isDelete = 0
    ORDER BY
        (SELECT COUNT(l)
         FROM Like l
         WHERE l.post = p) DESC,
        p.createdAt DESC
    """)
    Page<PostResponse> getTrendingFeed(
            @Param("currentUserId") Long currentUserId,
            Pageable pageable
    );


    // LIKED FEED: Những bài user đã thích
    @Query("""
    SELECT new com.duong.autumn.dto.response.PostResponse(
        p.id,
        p.image,
        p.name,
        p.content,
        p.createdAt,
        u.id,
        u.name,
        u.avatar,

        (SELECT COUNT(l)
         FROM Like l
         WHERE l.post = p),

        (SELECT COUNT(c)
         FROM Comment c
         WHERE c.post = p
           AND c.isDelete = 0),

        true
    )
    FROM Like myLike
    JOIN myLike.post p
    JOIN p.user u
    WHERE p.isDelete = 0
      AND myLike.user.id = :currentUserId
          ORDER BY myLike.createdAt DESC
    """)
    Page<PostResponse> getLikedFeed(
            @Param("currentUserId") Long currentUserId,
            Pageable pageable
    );
}
