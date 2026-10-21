package com.duong.autumn.dto.response;

import org.springframework.data.domain.Slice;

import java.util.List;

/** Không trả totalElements/totalPages vì truy vấn chỉ cần biết còn trang kế tiếp. */
public record SearchPageResponse<T>(List<T> items, int page, int size, boolean hasMore) {
    public static <T> SearchPageResponse<T> from(Slice<T> slice) {
        return new SearchPageResponse<>(slice.getContent(), slice.getNumber(),
                slice.getSize(), slice.hasNext());
    }
}

