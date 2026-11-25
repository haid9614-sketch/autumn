package com.duong.autumn.dto.response;

import java.time.LocalDateTime;

public record ConversationResponse(
        Long id,
        Long partnerId,
        String partnerName,
        String partnerUsername,
        String partnerAvatar,
        Long lastMessageId,
        Long lastMessageSenderId,
        String lastMessageContent,
        LocalDateTime lastMessageAt,
        LocalDateTime updatedAt
) {}
