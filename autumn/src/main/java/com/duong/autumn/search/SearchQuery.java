package com.duong.autumn.search;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;

/** Tham số đã kiểm tra; ! là ký tự escape thống nhất với hai truy vấn LIKE. */
public record SearchQuery(
        String keyword,
        String containsPattern,
        String prefixPattern,
        PageRequest pageable
) {
    public static SearchQuery of(String rawKeyword, int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page phải từ 0 trở lên.");
        }
        if (size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size phải nằm trong khoảng 1 đến 50.");
        }
        // JPA nhận offset kiểu int, trong khi PageRequest tính offset kiểu long.
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trang tìm kiếm quá lớn.");
        }
        String keyword = rawKeyword == null ? ""
                : Normalizer.normalize(rawKeyword.strip(), Normalizer.Form.NFC);
        if (keyword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keyword không được để trống.");
        }
        if (keyword.codePointCount(0, keyword.length()) > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keyword không được vượt quá 100 ký tự.");
        }
        String escaped = keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return new SearchQuery(keyword, "%" + escaped + "%", escaped + "%", PageRequest.of(page, size));
    }
}
