package com.duong.autumn.repository;

import com.duong.autumn.entity.ContentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface ContentReportRepository extends JpaRepository<ContentReport, Long> {
    List<ContentReport> findByUserId(Long userId);

    @Query("""
            SELECT r.targetId FROM ContentReport r
            WHERE r.userId = :viewerId AND r.targetType = :targetType
            """)
    Set<Long> findReportedTargetIds(@Param("viewerId") Long viewerId,
                                    @Param("targetType") String targetType);
}