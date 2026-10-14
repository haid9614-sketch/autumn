package com.duong.autumn.repository;
import com.duong.autumn.entity.TrendingFeedSnapshot;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TrendingFeedSnapshotRepository extends JpaRepository<TrendingFeedSnapshot, String> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT s FROM TrendingFeedSnapshot s WHERE s.id = :id")
    Optional<TrendingFeedSnapshot> findForRead(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TrendingFeedSnapshot s WHERE s.id = :id")
    Optional<TrendingFeedSnapshot> findForWrite(@Param("id") String id);

    @Query("""
        SELECT s.id FROM TrendingFeedSnapshot s
        WHERE NOT EXISTS (SELECT h.id FROM TrendingFeedHead h WHERE h.snapshotId = s.id)
          AND NOT EXISTS (SELECT v.id FROM TrendingFeedSession v WHERE v.snapshotId = s.id)
        ORDER BY s.createdAt ASC, s.id ASC
        """)
    List<String> findUnusedIds(Pageable pageable);
}
