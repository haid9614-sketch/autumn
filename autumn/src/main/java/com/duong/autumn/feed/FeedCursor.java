package com.duong.autumn.feed;

import java.time.LocalDateTime;

public record FeedCursor(
        int version,
        FeedType type,
        Long userId,
        LocalDateTime afterCreatedAt,
        Long afterId,
        Long afterLikeCount,
        String snapshotId,
        String sessionId,
        Long profileUserId
) {
    // Cursor của ba feed không gắn với một trang cá nhân.
    public FeedCursor(int version, FeedType type, Long userId,
                      LocalDateTime afterCreatedAt, Long afterId,
                      Long afterLikeCount, String snapshotId, String sessionId) {
        this(version, type, userId, afterCreatedAt, afterId, afterLikeCount,
                snapshotId, sessionId, null);
    }

    public FeedCursor(int version, FeedType type, Long userId,
                      LocalDateTime afterCreatedAt, Long afterId,
                      Long afterLikeCount, String snapshotId) {
        this(version, type, userId, afterCreatedAt, afterId, afterLikeCount, snapshotId, null, null);
    }
}

