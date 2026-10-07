package com.duong.autumn.repository;

import com.duong.autumn.entity.ImageAsset;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ImageAssetRepository extends JpaRepository<ImageAsset, Long> {
    // Các luồng đăng bài, đổi avatar và xóa ảnh khóa cùng một dòng asset.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a FROM ImageAsset a
            WHERE a.imageUrl = :imageUrl AND a.ownerId = :ownerId
            """)
    Optional<ImageAsset> findOwnedForUpdate(
            @Param("imageUrl") String imageUrl,
            @Param("ownerId") Long ownerId);
}
