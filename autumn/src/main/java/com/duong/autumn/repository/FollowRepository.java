package com.duong.autumn.repository;

import com.duong.autumn.entity.Follow;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    @Query("""
            SELECT COUNT(f) FROM Follow f
            WHERE f.follower.id = :followerId AND f.following.isDelete = 0
            """)
    long countByFollowerId(@Param("followerId") Long followerId);
}

