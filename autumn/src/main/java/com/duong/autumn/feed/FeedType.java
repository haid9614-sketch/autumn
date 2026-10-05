package com.duong.autumn.feed;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

public enum FeedType {
    STANDARD, TRENDING, LIKED;

    public static FeedType parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Loại feed phải là standard, trending hoặc liked.");
        }
    }
}

