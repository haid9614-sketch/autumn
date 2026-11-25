package com.duong.autumn.repository;

import com.duong.autumn.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {
    List<UserBlock> findByUserId(Long userId);
    boolean existsByUserIdAndBlockedUserId(Long userId, Long blockedUserId);
    void deleteByUserIdAndBlockedUserId(Long userId, Long blockedUserId);

    // Tự động hủy theo dõi 2 chiều khi chặn nhau
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM Follow f
            WHERE (f.follower.id = :userId AND f.following.id = :targetUserId)
               OR (f.follower.id = :targetUserId AND f.following.id = :userId)
            """)
    void deleteFollowsBetween(@Param("userId") Long userId, @Param("targetUserId") Long targetUserId);

    @Query("""
            SELECT CASE WHEN COUNT(ub) > 0 THEN true ELSE false END
            FROM UserBlock ub
            WHERE (ub.userId = :u1 AND ub.blockedUserId = :u2)
               OR (ub.userId = :u2 AND ub.blockedUserId = :u1)
            """)
    boolean existsBlockBetween(@Param("u1") Long u1, @Param("u2") Long u2);

}

