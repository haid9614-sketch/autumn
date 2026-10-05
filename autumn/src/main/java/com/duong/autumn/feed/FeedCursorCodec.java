package com.duong.autumn.feed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FeedCursorCodec {

    private final ObjectMapper objectMapper;
    public FeedCursorCodec() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule()); // Đăng ký hỗ trợ Java 8 LocalDateTime
    }

    public String encode(FeedCursor cursor) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(cursor));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể tạo cursor.", exception);
        }
    }

    public FeedCursor decode(String token, FeedType expectedType, Long userId) {
        if (token == null || token.isBlank()) {
            return null; // Lần tải đầu, hoặc tải lại bảng tin.
        }
        if (token.length() > 2048) {
            throw invalidCursor();
        }
        try {
            FeedCursor cursor = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(token), FeedCursor.class);
            if (cursor == null || cursor.version() != 1
                    || cursor.type() != expectedType
                    || !Objects.equals(cursor.userId(), userId)
                    || cursor.afterCreatedAt() == null
                    || cursor.afterId() == null || cursor.afterId() <= 0
                    || cursor.afterLikeCount() == null || cursor.afterLikeCount() < 0) {
                throw invalidCursor();
            }
            if (expectedType == FeedType.TRENDING) {
                if (cursor.snapshotId() == null) {
                    throw invalidCursor();
                }
                UUID.fromString(cursor.snapshotId());
            } else if (cursor.snapshotId() != null || cursor.afterLikeCount() != 0L) {
                throw invalidCursor();
            }
            return cursor;
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    private ResponseStatusException invalidCursor() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cursor không hợp lệ hoặc không thuộc bảng tin/tài khoản này.");
    }
}
