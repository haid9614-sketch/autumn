package com.duong.autumn.repository;

import com.duong.autumn.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;
import com.duong.autumn.dto.response.UserSearchResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByAvatar(String avatar);

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.isDelete = 0")
    Optional<User> findActiveById(@Param("id") Long id);

    // tim kiem user

    @Query("""
            SELECT new com.duong.autumn.dto.response.UserSearchResponse(u.id, u.avatar, u.name, u.username)
            FROM User u
            WHERE u.isDelete = 0
              AND (
                  collate(u.username as utf8mb4_0900_ai_ci) LIKE :containsPattern ESCAPE '!'
                  OR collate(u.name as utf8mb4_0900_ai_ci) LIKE :containsPattern ESCAPE '!'
              )
            ORDER BY CASE
                WHEN collate(u.username as utf8mb4_0900_ai_ci) = :keyword THEN 0
                WHEN collate(u.username as utf8mb4_0900_ai_ci) LIKE :prefixPattern ESCAPE '!' THEN 1
                WHEN collate(u.name as utf8mb4_0900_ai_ci) LIKE :prefixPattern ESCAPE '!' THEN 2
                ELSE 3
            END,
            collate(u.username as utf8mb4_0900_ai_ci) ASC, u.id ASC
            """)
    Slice<UserSearchResponse> searchActiveUsers(
            @Param("keyword") String keyword,
            @Param("containsPattern") String containsPattern,
            @Param("prefixPattern") String prefixPattern,
            Pageable pageable
    );

    // Tăng tokenVersion trực tiếp dưới DB để vô hiệu hóa toàn bộ JWT cũ (Khi Logout hoặc Đổi mật khẩu)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE User u SET u.tokenVersion = COALESCE(u.tokenVersion, 0) + 1 WHERE u.id = :userId")
    int incrementTokenVersion(@Param("userId") Long userId);

    // Cập nhật riêng cột avatar để không bao giờ ghi đè mật khẩu hay tokenVersion (Sửa dứt điểm BUG-01)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE User u SET u.avatar = :avatar WHERE u.id = :userId AND u.isDelete = 0")
    int updateAvatarById(@Param("userId") Long userId, @Param("avatar") String avatar);

}