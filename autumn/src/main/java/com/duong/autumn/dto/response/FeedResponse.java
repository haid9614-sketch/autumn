package com.duong.autumn.dto.response;

import java.util.List;

public record FeedResponse(
        List<PostResponse> items,
        String nextCursor,
        boolean hasMore,
        String feedSessionId
) {
    public FeedResponse(List<PostResponse> items, String nextCursor, boolean hasMore) {
        this(items, nextCursor, hasMore, null);
    }
}
