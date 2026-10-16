package com.duong.autumn.repository;

import com.duong.autumn.entity.Follow;
import com.duong.autumn.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface FollowRepository extends JpaRepository<Follow, Long> {

    // Đếm số người đang hoạt động mà followerId đang theo dõi (followingCount)
    @Query("""
            SELECT COUNT(f) FROM Follow f
            WHERE f.follower.id = :followerId AND f.following.isDelete = 0
            """)
    long countByFollowerId(@Param("followerId") Long followerId);

    // Đếm số người đang hoạt động đang theo dõi followingId (followerCount) số fl của người mình đang xem in4
    @Query("""
            SELECT COUNT(f) FROM Follow f
            WHERE f.following.id = :followingId AND f.follower.isDelete = 0
            """)
    long countByFollowingId(@Param("followingId") Long followingId);

    // Kiểm tra A đã follow B chưa
    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    // Xóa trực tiếp quan hệ follow (dùng cho cả Unfollow và Remove Follower)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM Follow f
            WHERE f.follower.id = :followerId AND f.following.id = :followingId
            """)
    int deleteByFollowerIdAndFollowingId(@Param("followerId") Long followerId,
                                         @Param("followingId") Long followingId);

    // Lấy danh sách những người đang theo dõi userId (Followers) - chỉ lấy tài khoản chưa xóa
    @Query("""
            SELECT f.follower FROM Follow f
            WHERE f.following.id = :userId AND f.follower.isDelete = 0
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<User> findActiveFollowersByUserId(@Param("userId") Long userId);

    // Lấy danh sách những người mà userId đang theo dõi (Following) - chỉ lấy tài khoản chưa xóa
    @Query("""
            SELECT f.following FROM Follow f
            WHERE f.follower.id = :userId AND f.following.isDelete = 0
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<User> findActiveFollowingByUserId(@Param("userId") Long userId);

    // Batch Query chống N+1: Trong tập targetIds, lấy ra các ID mà followerId đang theo dõi
    @Query("""
            SELECT f.following.id FROM Follow f
            WHERE f.follower.id = :followerId AND f.following.id IN :targetIds
            """)
    Set<Long> findFollowingIdsIn(@Param("followerId") Long followerId,
                                 @Param("targetIds") Collection<Long> targetIds);

    // Batch Query chống N+1: Trong tập targetIds, lấy ra các ID đang theo dõi followingId
    @Query("""
            SELECT f.follower.id FROM Follow f
            WHERE f.following.id = :followingId AND f.follower.id IN :targetIds
            """)
    Set<Long> findFollowerIdsIn(@Param("followingId") Long followingId,
                                @Param("targetIds") Collection<Long> targetIds);
}
