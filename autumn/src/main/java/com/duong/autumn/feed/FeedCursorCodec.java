package com.duong.autumn.feed;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@Component
@RequiredArgsConstructor
public class FeedCursorCodec {
    private final ObjectMapper objectMapper;

    public String encode(FeedCursor cursor) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(cursor));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Không thể tạo cursor.", exception);
        }
    }

    // Kiểm tra đúng một loại feed; giữ method để các caller cũ có thể dùng.
    public FeedCursor decode(String token, FeedType expectedType, Long userId) {
        FeedCursor cursor = read(token, userId);
        if (cursor != null && cursor.type() != expectedType) {
            throw invalidCursor();
        }
        return cursor;
    }

    // /standard được phép tiếp tục phiên TRENDING đã phát khi chưa follow ai.
    public FeedCursor decodeForRequest(String token, FeedType requestedType, Long userId) {
        FeedCursor cursor = read(token, userId);
        if (cursor != null && cursor.type() != requestedType
                && !(requestedType == FeedType.STANDARD && cursor.type() == FeedType.TRENDING)) {
            throw invalidCursor();
        }
        return cursor;
    }

    private FeedCursor read(String token, Long userId) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (token.length() > 2048) {
            throw invalidCursor();
        }
        try {
            FeedCursor cursor = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(token), FeedCursor.class);
            if (cursor == null || (cursor.version() != 1 && cursor.version() != 2) || cursor.type() == null
                    || userId == null || !Objects.equals(cursor.userId(), userId)
                    || cursor.afterCreatedAt() == null || cursor.afterId() == null
                    || cursor.afterId() <= 0 || cursor.afterLikeCount() == null
                    || cursor.afterLikeCount() < 0) {
                throw invalidCursor();
            }
            if (cursor.type() == FeedType.TRENDING) {
                if (cursor.snapshotId() == null || !UUID.fromString(cursor.snapshotId())
                        .toString().equalsIgnoreCase(cursor.snapshotId())) {
                    throw invalidCursor();
                }
                if (cursor.version() == 2 && (cursor.sessionId() == null
                        || !UUID.fromString(cursor.sessionId()).toString().equals(cursor.sessionId()))) {
                    throw invalidCursor();
                }
                if (cursor.version() == 1 && cursor.sessionId() != null) throw invalidCursor();
            } else if (cursor.snapshotId() != null || cursor.sessionId() != null
                    || cursor.afterLikeCount() != 0L) {
                throw invalidCursor();
            }
            return cursor;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    private ResponseStatusException invalidCursor() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cursor không hợp lệ hoặc không thuộc bảng tin/tài khoản này.");
    }
}
