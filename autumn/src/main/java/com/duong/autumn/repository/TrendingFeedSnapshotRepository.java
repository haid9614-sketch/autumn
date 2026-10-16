package com.duong.autumn.repository;
import com.duong.autumn.entity.TrendingFeedSnapshot;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TrendingFeedSnapshotRepository extends JpaRepository<TrendingFeedSnapshot, String> {
    // Chỉ thấy snapshot đã commit. id phá hòa nếu hai createdAt bằng nhau.
    Optional<TrendingFeedSnapshot> findFirstByOrderByCreatedAtDescIdDesc();

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT s FROM TrendingFeedSnapshot s WHERE s.id = :id")
    Optional<TrendingFeedSnapshot> findForRead(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TrendingFeedSnapshot s WHERE s.id = :id")
    Optional<TrendingFeedSnapshot> findForWrite(@Param("id") String id);

    @Query("""
        SELECT s.id FROM TrendingFeedSnapshot s
        WHERE s.id <> :latestId
          AND NOT EXISTS (SELECT v.id FROM TrendingFeedSession v WHERE v.snapshotId = s.id)
        ORDER BY s.createdAt ASC, s.id ASC
        """)
    List<String> findUnusedIds(@Param("latestId") String latestId, Pageable pageable);
}