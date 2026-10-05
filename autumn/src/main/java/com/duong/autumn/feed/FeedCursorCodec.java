package com.duong.autumn.feed;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FeedCursorCodec {

    private final ObjectMapper objectMapper;

    public String encode(FeedCursor cursor) {
        try {
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            objectMapper.writeValueAsBytes(cursor)
                    );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Không thể tạo cursor.",
                    exception
            );
        }
    }

    public FeedCursor decode(
            String token,
            FeedType expectedType,
            Long userId
    ) {
        // Chưa có cursor: tải những bài đầu tiên.
        if (token == null || token.isBlank()) {
            return null;
        }

        if (token.length() > 2048) {
            throw invalidCursor();
        }

        try {
            // Giải mã Base64, rồi chuyển JSON về FeedCursor.
            FeedCursor cursor = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(token),
                    FeedCursor.class
            );

            if (cursor == null
                    || cursor.version() != 1
                    || cursor.type() != expectedType
                    || !Objects.equals(cursor.userId(), userId)
                    || cursor.afterCreatedAt() == null
                    || cursor.afterId() == null
                    || cursor.afterId() <= 0
                    || cursor.afterLikeCount() == null
                    || cursor.afterLikeCount() < 0) {
                throw invalidCursor();
            }

            if (expectedType == FeedType.TRENDING) {
                // Trending cần xác định phiên giữ thứ tự bài viết.
                if (cursor.snapshotId() == null) {
                    throw invalidCursor();
                }

                UUID.fromString(cursor.snapshotId());
            } else if (cursor.snapshotId() != null
                    || cursor.afterLikeCount() != 0L) {
                throw invalidCursor();
            }

            return cursor;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    private ResponseStatusException invalidCursor() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cursor không hợp lệ hoặc không thuộc bảng tin/tài khoản này."
        );
    }
}