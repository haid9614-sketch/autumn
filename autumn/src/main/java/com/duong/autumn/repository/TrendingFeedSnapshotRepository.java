package com.duong.autumn.repository;

import com.duong.autumn.entity.TrendingFeedSnapshot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface TrendingFeedSnapshotRepository
        extends JpaRepository<TrendingFeedSnapshot, String> {

    // Khóa đọc giữ phiên tồn tại cho đến khi lấy xong batch hiện tại.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT s FROM TrendingFeedSnapshot s WHERE s.id = :id")
    Optional<TrendingFeedSnapshot> findForRead(@Param("id") String id);

    @Modifying
    @Query("DELETE FROM TrendingFeedSnapshot s WHERE s.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}

