package com.duong.autumn.dto.response;

import java.util.List;

public record MessagePageResponse(
        List<ChatMessageResponse> items,
        Long nextBeforeId,
        Long latestId,
        boolean hasMore
) {}
