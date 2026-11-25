package com.duong.autumn.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationPageResponse(
        List<ConversationResponse> items,
        LocalDateTime nextBeforeUpdatedAt,
        Long nextBeforeId,
        boolean hasMore
) {}
