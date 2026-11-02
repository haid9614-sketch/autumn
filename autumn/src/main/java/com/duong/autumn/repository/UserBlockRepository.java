package com.duong.autumn.repository;

import com.duong.autumn.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {
    List<UserBlock> findByUserId(Long userId);
    boolean existsByUserIdAndBlockedUserId(Long userId, Long blockedUserId);
    void deleteByUserIdAndBlockedUserId(Long userId, Long blockedUserId);

    // Lấy toàn bộ userId cần ẩn (gồm người mình đã chặn VÀ người đã chặn mình)
    @Query("""
            SELECT CASE WHEN b.userId = :viewerId THEN b.blockedUserId ELSE b.userId END
            FROM UserBlock b
            WHERE b.userId = :viewerId OR b.blockedUserId = :viewerId
            """)
    Set<Long> findHiddenUserIds(@Param("viewerId") Long viewerId);
}