package com.duong.autumn.repository;

import com.duong.autumn.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    List<DeviceToken> findAllByFcmToken(String fcmToken);

    @Query("SELECT d FROM DeviceToken d WHERE d.user.id = :userId")
    List<DeviceToken> findByUserId(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DeviceToken d WHERE d.user.id = :userId AND d.fcmToken = :token")
    int deleteByUserIdAndFcmToken(@Param("userId") Long userId, @Param("token") String token);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DeviceToken d WHERE d.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DeviceToken d WHERE d.fcmToken IN :tokens")
    int deleteByFcmTokenIn(@Param("tokens") List<String> tokens);
}
