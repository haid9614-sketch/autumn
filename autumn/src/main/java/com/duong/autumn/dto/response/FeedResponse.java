package com.duong.autumn.dto.response;

import java.util.List;

public record FeedResponse(
        List<PostResponse> items,
        String nextCursor,
        boolean hasMore
) {
}
