package com.duong.autumn.dto.response;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        String senderName,
        String senderUsername,
        String senderAvatar,
        String content,
        LocalDateTime createdAt
) {}
