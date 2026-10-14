package com.duong.autumn.feed;

/** id phiên xem khác với id bản xếp hạng dùng chung. */
public record TrendingFeedSessionRef(String sessionId, String snapshotId) {}
