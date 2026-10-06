package com.duong.autumn.dto.internal;

import com.duong.autumn.dto.response.PostResponse;
import lombok.Getter;

import java.time.LocalDateTime;

/** Metadata dùng trong backend để tạo cursor, không trả ra giao diện. */
@Getter
public class FeedRow {
    private final PostResponse post;
    private final LocalDateTime cursorCreatedAt;
    private final Long cursorId;
    private final Long cursorLikeCount;

    public FeedRow(Long id, String image, String name, String content,
                   LocalDateTime createdAt, Long authorId, String authorName,
                   String authorAvatar, Long likeCount, Long commentCount,
                   Boolean isLiked, LocalDateTime cursorCreatedAt,
                   Long cursorId, Long cursorLikeCount) {
        this.post = new PostResponse(id, image, name, content, createdAt,
                authorId, authorName, authorAvatar, likeCount, commentCount, isLiked);
        this.cursorCreatedAt = cursorCreatedAt;
        this.cursorId = cursorId;
        this.cursorLikeCount = cursorLikeCount;
    }
}

