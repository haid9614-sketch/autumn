package com.duong.autumn.repository;

import com.duong.autumn.entity.Like;
import com.duong.autumn.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LikeRepository extends JpaRepository<Like, Long> {

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Like l WHERE l.user.id = :userId AND l.post.id = :postId")
    int deleteByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);

    // Lấy danh sách người dùng đã thả tim bài viết (chỉ lấy user chưa bị xóa, mới nhất lên đầu)
    @Query("""
            SELECT l.user FROM Like l
            WHERE l.post.id = :postId AND l.user.isDelete = 0
            ORDER BY l.createdAt DESC, l.id DESC
            """)
    List<User> findActiveLikersByPostId(@Param("postId") Long postId);
}