package com.duong.autumn.repository;
import com.duong.autumn.entity.TrendingFeedSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface TrendingFeedSessionRepository extends JpaRepository<TrendingFeedSession, String> {
    // Cùng sessionId không tạo thêm phiên. Marker đóng cũng chặn request đến muộn.
    @Modifying
    @Query(value = """
        INSERT INTO feed_trending_sessions (id, user_id, created_at)
        VALUES (:id, :userId, :now)
        ON DUPLICATE KEY UPDATE id = id
        """, nativeQuery = true)
    int ensureExists(@Param("id") String id, @Param("userId") Long userId,
                     @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM TrendingFeedSession v WHERE v.id = :id")
    Optional<TrendingFeedSession> findForWrite(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT v FROM TrendingFeedSession v WHERE v.id = :id")
    Optional<TrendingFeedSession> findForRead(@Param("id") String id);

    boolean existsBySnapshotId(String snapshotId);

    @Modifying
    @Query("DELETE FROM TrendingFeedSession v WHERE v.releasedAt < :cutoff")
    int deleteReleasedBefore(@Param("cutoff") Instant cutoff);
}
