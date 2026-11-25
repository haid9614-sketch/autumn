package com.duong.autumn.repository;

import com.duong.autumn.dto.response.ChatMessageResponse;
import com.duong.autumn.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
            SELECT m FROM Message m
            JOIN FETCH m.sender s
            JOIN FETCH m.conversation c
            WHERE m.id = :messageId AND m.isDelete = 0
            """)
    Optional<Message> findActiveByIdWithRelations(@Param("messageId") Long messageId);

    // Tải trang đầu hoặc cuộn ngược lấy tin cũ hơn mốc beforeId (Keyset Pagination).
    @Query("""
            SELECT new com.duong.autumn.dto.response.ChatMessageResponse(
                m.id, c.id, s.id, s.name, s.username, s.avatar, m.content, m.createdAt
            )
            FROM Message m
            JOIN m.conversation c
            JOIN m.sender s
            WHERE c.id = :conversationId
              AND m.isDelete = 0
              AND (:beforeId IS NULL OR m.id < :beforeId)
            ORDER BY m.id DESC
            """)
    List<ChatMessageResponse> findMessagesBefore(
            @Param("conversationId") Long conversationId,
            @Param("beforeId") Long beforeId,
            Pageable pageable
    );

    // Polling lấy các tin nhắn mới hơn mốc afterId (tăng dần theo thứ tự gửi).
    @Query("""
            SELECT new com.duong.autumn.dto.response.ChatMessageResponse(
                m.id, c.id, s.id, s.name, s.username, s.avatar, m.content, m.createdAt
            )
            FROM Message m
            JOIN m.conversation c
            JOIN m.sender s
            WHERE c.id = :conversationId
              AND m.isDelete = 0
              AND m.id > :afterId
            ORDER BY m.id ASC
            """)
    List<ChatMessageResponse> findMessagesAfter(
            @Param("conversationId") Long conversationId,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    // Lấy tin nhắn cuối cùng cho cả trang hộp thư trong đúng 1 truy vấn (chống N+1).
    @Query("""
            SELECT m FROM Message m
            JOIN FETCH m.sender s
            JOIN FETCH m.conversation c
            WHERE m.id IN (
                SELECT MAX(m2.id) FROM Message m2
                WHERE m2.conversation.id IN :conversationIds
                  AND m2.isDelete = 0
                GROUP BY m2.conversation.id
            )
            """)
    List<Message> findLatestMessagesByConversationIds(@Param("conversationIds") List<Long> conversationIds);
}