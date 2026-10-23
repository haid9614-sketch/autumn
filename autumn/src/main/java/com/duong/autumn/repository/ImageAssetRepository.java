package com.duong.autumn.repository;

import com.duong.autumn.entity.ImageAsset;
import com.duong.autumn.entity.ImageAssetState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

public interface ImageAssetRepository extends JpaRepository<ImageAsset, Long> {
    // Mỗi lượt dọn tối đa 50 ảnh đã được DB xác nhận không còn dùng.
    List<ImageAsset> findTop50ByStateOrderByIdAsc(ImageAssetState state);

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
