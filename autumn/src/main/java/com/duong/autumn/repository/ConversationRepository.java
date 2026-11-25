package com.duong.autumn.repository;

import com.duong.autumn.entity.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    // Tạo phòng chat nguyên tử theo cặp (u1 < u2), chống Race Condition khi 2 người mở cùng lúc.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO conversations (user1_id, user2_id, is_delete, created_at, updated_at)
            VALUES (:u1, :u2, 0, :now, :now)
            ON DUPLICATE KEY UPDATE is_delete = 0
            """, nativeQuery = true)
    int ensureConversation(
            @Param("u1") Long u1,
            @Param("u2") Long u2,
            @Param("now") LocalDateTime now
    );

    @Query("""
            SELECT c FROM Conversation c
            JOIN FETCH c.user1 u1
            JOIN FETCH c.user2 u2
            WHERE c.id = :id AND c.isDelete = 0
            """)
    Optional<Conversation> findActiveByIdWithUsers(@Param("id") Long id);

    @Query("""
            SELECT c FROM Conversation c
            JOIN FETCH c.user1 u1
            JOIN FETCH c.user2 u2
            WHERE u1.id = :u1 AND u2.id = :u2 AND c.isDelete = 0
            """)
    Optional<Conversation> findActiveByPair(@Param("u1") Long u1, @Param("u2") Long u2);

    // Đẩy cuộc trò chuyện lên đầu hộp thư mỗi khi có tin nhắn mới.
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("UPDATE Conversation c SET c.updatedAt = :now WHERE c.id = :conversationId")
    int touchUpdatedAt(@Param("conversationId") Long conversationId, @Param("now") LocalDateTime now);

    // Danh sách hộp thư theo Keyset Cursor (ẩn người đã xóa, người đã chặn và phòng chưa có tin nhắn).
    @Query("""
            SELECT c FROM Conversation c
            JOIN FETCH c.user1 u1
            JOIN FETCH c.user2 u2
            WHERE c.isDelete = 0
              AND (u1.id = :currentUserId OR u2.id = :currentUserId)
              AND u1.isDelete = 0
              AND u2.isDelete = 0
              AND EXISTS (
                  SELECT m.id FROM Message m
                  WHERE m.conversation = c AND m.isDelete = 0
              )
              AND NOT EXISTS (
                  SELECT ub.id FROM UserBlock ub
                  WHERE (ub.userId = u1.id AND ub.blockedUserId = u2.id)
                     OR (ub.userId = u2.id AND ub.blockedUserId = u1.id)
              )
              AND (
                  :beforeUpdatedAt IS NULL
                  OR c.updatedAt < :beforeUpdatedAt
                  OR (c.updatedAt = :beforeUpdatedAt AND c.id < :beforeId)
              )
            ORDER BY c.updatedAt DESC, c.id DESC
            """)
    List<Conversation> findInboxConversations(
            @Param("currentUserId") Long currentUserId,
            @Param("beforeUpdatedAt") LocalDateTime beforeUpdatedAt,
            @Param("beforeId") Long beforeId,
            Pageable pageable
    );
}