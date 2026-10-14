package com.duong.autumn.repository;
import com.duong.autumn.entity.TrendingFeedHead;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface TrendingFeedHeadRepository extends JpaRepository<TrendingFeedHead, Integer> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT h FROM TrendingFeedHead h WHERE h.id = 1")
    Optional<TrendingFeedHead> findForRead();

    // Khóa riêng cho job; không khóa head trong lúc INSERT ... SELECT.
    @Query(value = "SELECT id FROM feed_trending_build_lock WHERE id = 1 FOR UPDATE",
            nativeQuery = true)
    Integer acquireBuildLock();

    boolean existsBySnapshotId(String snapshotId);
}
